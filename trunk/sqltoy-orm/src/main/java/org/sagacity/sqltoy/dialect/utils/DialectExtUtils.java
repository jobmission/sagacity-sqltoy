/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 将原本DialectUtils中的部分功能抽离出来,从而避免DialectUtils跟一些类之间的互相调用
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月30日
 * @modify 2022-10-19 修改processDefaultValue修复oracle、db2日期类型的支持
 */
public class DialectExtUtils {

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName, dbType));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		boolean isSupportNULL = StringUtil.isBlank(isNullFunction) ? false : true;
		String columnName;
		boolean isString = false;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			isString = false;
			if ("java.lang.string".equals(fieldMeta.getFieldType())) {
				isString = true;
			}
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					// 目前只有mysql支持
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					if (isAssignPK && isSupportNULL) {
						values.append(isNullFunction);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(columnName);
				// kudu 中文会产生乱码
				if (dbType == DBType.IMPALA && isString) {
					values.append("cast(? as string)");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}
		sql.append(") ");
		sql.append(" values (");
		sql.append(values);
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 统一对表字段默认值进行处理,主要针对merge into 等sql语句
	 * @param sql
	 * @param dbType
	 * @param fieldMeta
	 * @param defaultValue
	 */
	public static void processDefaultValue(StringBuilder sql, int dbType, FieldMeta fieldMeta, String defaultValue) {
		// EntityManager解析时已经小写化处理
		String fieldType = fieldMeta.getFieldType();
		// 是否是各种数据库的当前时间、日期的字符
		String defaultLow = defaultValue.toLowerCase();
		boolean isCurrentTime = SqlUtilsExt.isCurrentTime(defaultLow);
		// 无法解决同一个POJO的默认值注解在不同数据库下的兼容
		// if (isCurrentTime) {
		// sql.append(defaultValue);
		// return;
		// }
		int dateType = -1;
		// 时间
		if ("java.time.localtime".equals(fieldType) || "java.sql.time".equals(fieldType)) {
			dateType = 1;
		} else if ("java.time.localdate".equals(fieldType)) {
			dateType = 2;
		} else if ("java.time.localdatetime".equals(fieldType) || "java.util.date".equals(fieldType)
				|| "java.sql.date".equals(fieldType)) {
			dateType = 3;
		} else if ("java.sql.timestamp".equals(fieldType) || "oracle.sql.timestamp".equals(fieldType)) {
			dateType = 4;
		}
		String dateStr;
		if (isCurrentTime) {
			if (dateType == 1) {
				dateStr = DateUtil.formatDate(DateUtil.getNowTime(), "HH:mm:ss");
			} else if (dateType == 2) {
				dateStr = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd");
			} else if (dateType == 3) {
				dateStr = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd HH:mm:ss");
			} else {
				dateStr = DateUtil.formatDate(DateUtil.getNowTime(), "yyyy-MM-dd HH:mm:ss.SSS");
			}
		} else {
			dateStr = defaultValue;
		}

		// 日期类型
		if (dateType != -1) {
			if (!dateStr.startsWith("'") && !dateStr.endsWith("'")) {
				dateStr = "'".concat(dateStr).concat("'");
			}
			// oracle、db2支持merge into场景(sqlserver具有自行转换能力，无需进行格式转换)
			if (dateType == 1) {
				if (dbType == DBType.DB2) {
					dateStr = "time(" + dateStr + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					// oracle 没有time类型,因此本行的逻辑实际不会生效
					dateStr = "to_date(" + dateStr + ",'HH24:mi:ss')";
				}
			} else if (dateType == 2) {
				if (dbType == DBType.DB2) {
					dateStr = "date(" + dateStr + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					dateStr = "to_date(" + dateStr + ",'yyyy-MM-dd')";
				}
			} else if (dateType == 3) {
				if (dbType == DBType.DB2) {
					dateStr = "timestamp(" + dateStr + ")";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					dateStr = "to_date(" + dateStr + ",'yyyy-MM-dd HH24:mi:ss')";
				}
			} // timestamp 类型进行特殊处理，避免批量插入时，所有记录时间一致导致精度损失
			else if (dateType == 4) {
				if (dbType == DBType.DB2) {
					dateStr = "CURRENT TIMESTAMP";
				} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.MYSQL
						|| dbType == DBType.MYSQL57 || dbType == DBType.POSTGRESQL || dbType == DBType.DM
						|| dbType == DBType.GAUSSDB || dbType == DBType.OCEANBASE || dbType == DBType.SQLITE
						|| dbType == DBType.KINGBASE || dbType == DBType.TIDB) {
					dateStr = "CURRENT_TIMESTAMP";
				} else if (isCurrentTime) {
					dateStr = defaultValue;
				}
			}
		}
		sql.append(dateStr);
	}

	/**
	 * @TODO 组织判断unique的sql(从DialectUtils中抽离避免循环调用)
	 * @param entityMeta
	 * @param realParamNamed
	 * @param dbType
	 * @param tableName
	 * @return
	 */
	public static String wrapUniqueSql(EntityMeta entityMeta, String[] realParamNamed, Integer dbType,
			String tableName) {
		// 构造查询语句(固定1避免无主键表导致select from 问题)
		StringBuilder queryStr = new StringBuilder("select 1 ");
		// 如果存在主键，则查询主键字段
		if (null != entityMeta.getIdArray()) {
			for (String idFieldName : entityMeta.getIdArray()) {
				queryStr.append(",");
				queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(idFieldName), dbType));
			}
		}
		queryStr.append(" from ");
		queryStr.append(entityMeta.getSchemaTable(tableName, dbType));
		queryStr.append(" where  ");
		for (int i = 0; i < realParamNamed.length; i++) {
			if (i > 0) {
				queryStr.append(" and ");
			}
			queryStr.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(realParamNamed[i]), dbType))
					.append("=:" + realParamNamed[i]);
		}
		return queryStr.toString();
	}

	/**
	 * @todo 处理加工对象基于dm、oceanbase、oracle数据库的saveIgnoreExist
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param fromTable
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String mergeIgnore(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy, String fromTable,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		// 在无主键的情况下产生insert sql语句
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(dbType, entityMeta, pkStrategy, isNullFunction, sequence, isAssignPK, realTable);
		}
		boolean isSupportNUL = StringUtil.isBlank(isNullFunction) ? false : true;
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append("? as ");
			sql.append(columnName);
		}
		if (StringUtil.isNotBlank(fromTable)) {
			sql.append(" from ").append(fromTable);
		}
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			FieldMeta fieldMeta;
			// update 只针对非主键字段进行修改
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (i > 0) {
					insertRejIdCols.append(",");
					insertRejIdColValues.append(",");
				}
				insertRejIdCols.append(columnName);
				insertRejIdColValues.append("tv.").append(columnName);
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK && isSupportNUL) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @TODO 针对postgresql\kingbase\guassdb等数据库
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String insertIgnore(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName, dbType));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		String columnName;
		boolean isStart = true;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (!isStart) {
				sql.append(",");
				values.append(",");
			}
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					if (isAssignPK) {
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					sql.append(columnName);
					values.append(isNullFunction).append("(?,").append(sequence).append(")");
					isStart = false;
				} else {
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				sql.append(columnName);
				values.append("?");
				isStart = false;
			}
		}
		sql.append(") values ( ");
		sql.append(values);
		sql.append(")");

		// 增加do noting
		if (entityMeta.getIdArray() != null) {
			sql.append(" ON CONFLICT (");
			for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
				if (i > 0) {
					sql.append(",");
				}
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
				sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			}
			sql.append(" ) DO NOTHING ");
		}
		return sql.toString();
	}

	/**
	 * @TODO 解决saveOrUpdate场景对一些记录无法判断是新增导致无法对创建人、创建时间等属性进行统一赋值，从而通过默认值模式来解决
	 * @param createUnifyFields
	 * @param dbType
	 * @param fieldMeta
	 * @return
	 */
	public static String getInsertDefaultValue(IgnoreKeyCaseMap<String, Object> createUnifyFields, Integer dbType,
			FieldMeta fieldMeta) {
		if (createUnifyFields == null || createUnifyFields.isEmpty()
				|| !createUnifyFields.containsKey(fieldMeta.getFieldName())) {
			return fieldMeta.getDefaultValue();
		}
		Object unifyFieldValue = createUnifyFields.get(fieldMeta.getFieldName());
		if (unifyFieldValue != null) {
			if (unifyFieldValue instanceof String) {
				return (String) unifyFieldValue;
			} else if (unifyFieldValue instanceof Number) {
				return unifyFieldValue.toString();
			} else {
				// entityManager已经做了小写化处理
				String fieldType = fieldMeta.getFieldType();
				if ("java.time.localdate".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_HORIZONTAL);
				} else if ("java.time.localtime".equals(fieldType) || "java.sql.time".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, "HH:mm:ss");
				} else if ("java.time.localdatetime".equals(fieldType) || "java.sql.timestamp".equals(fieldType)
						|| "java.util.date".equals(fieldType) || "java.sql.date".equals(fieldType)) {
					return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATETIME_HORIZONTAL);
				}
				// 统一传参数值为日期类型，但数据库中是数字或字符串类型
				if ((unifyFieldValue instanceof Date) || (unifyFieldValue instanceof LocalDate)
						|| (unifyFieldValue instanceof LocalDateTime)) {
					if ("java.lang.integer".equals(fieldType) || "int".equals(fieldType)) {
						return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_8CHAR);
					} else if ("java.lang.long".equals(fieldType) || "java.math.biginteger".equals(fieldType)
							|| "long".equals(fieldType)) {
						return DateUtil.formatDate(unifyFieldValue, "yyyyMMddHHmmss");
					} else if ("java.lang.string".equals(fieldType)) {
						if (fieldMeta.getLength() >= 19) {
							return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATETIME_HORIZONTAL);
						}
						if (fieldMeta.getLength() >= 14) {
							return DateUtil.formatDate(unifyFieldValue, "yyyyMMddHHmmss");
						}
						if (fieldMeta.getLength() >= 8) {
							return DateUtil.formatDate(unifyFieldValue, DateUtil.FORMAT.DATE_8CHAR);
						}
					}
				}
			}
		}
		return fieldMeta.getDefaultValue();
	}
}
