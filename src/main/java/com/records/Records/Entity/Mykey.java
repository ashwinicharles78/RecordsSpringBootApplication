package com.records.Records.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.List;

@Embeddable
public class Mykey implements Serializable {

    Mykey() {

    }

    public Mykey (String mykey) {
        String[] stringList = StringUtils.split(mykey, "-");
        if(stringList.length == 2) {
            for (String key : stringList) {
                if (Etype.NAME.name().equals(key) || Etype.PHONE.name().equals(key) || Etype.ADDRESS.name().equals(key)){
                    this.setType(Etype.valueOf(key));
                } else {
                    this.setValue(key);
                }
            }
        }
    }

    public Etype getType() {
        return type;
    }

    public void setType(Etype type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Column(name="type")
    private Etype type;

    @Column(name="value")
    private String value;
}
