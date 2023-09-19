package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class A1 implements Serializable {
    private int intNum;
    private Integer integerNum;
    private String str;
    private BigDecimal decimal;
    private short shortNum;
    private Short shortTNum;
    private Date date;
    private B1 b;
    private List<C1> c;
}
