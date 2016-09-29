/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.api.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A query context that stores properties describing contextual parameters of the query.
 *
 */
public class QueryContext {

    private Map<String,QueryContextProperty> properties;

    public QueryContext(){

            properties = new HashMap<>();

    }

    public Boolean hasProperty(String key){

            return this.properties.get(key) != null;

    }

    public Integer getIntProperty(String key){

        if(hasProperty(key)){

                return ((int)this.properties.get(key).getValue());

        } else {

                return null;

        }

    }

    public Long getLongProperty(String key){

        if(hasProperty(key)){

                return ((long)this.properties.get(key).getValue());

        } else {

                return null;

        }

    }

    public Boolean getBooleanProperty(String key){

        if(hasProperty(key)){

                return ((Boolean)this.properties.get(key).getValue());

        } else {

                return null;

        }

    }

    public String getStringProperty(String key){

        if(hasProperty(key)){

                return this.properties.get(key).getValue().toString();

        } else {

                return null;

        }

    }

    public QueryContextProperty getProperty(String key){

        if(hasProperty(key)){

            return this.properties.get(key);

        } else {

            return null;

        }

    }

    public List<QueryContextProperty> getProperties(){

            List<QueryContextProperty> props = new ArrayList<>();
            this.properties.keySet().forEach(

                    k -> props.add(this.properties.get(k))

            );

            return props;

    }


    public void longProperty(String key, long value){

            QueryContextProperty prop = new LongContextProperty(key, value);
            this.properties.put(key, prop);

    }

    public void intProperty(String key, int value){

            QueryContextProperty prop = new IntContextProperty(key, value);
            this.properties.put(key, prop);

    }

    public void booleanProperty(String key, Boolean value){

            QueryContextProperty prop = new BooleanContextProperty(key, value);
            this.properties.put(key, prop);

    }

    public void stringProperty(String key, String value){

            QueryContextProperty prop = new StringContextProperty(key, value);
            this.properties.put(key, prop);

    }

    public void addProperty(QueryContextProperty prop){

            this.properties.put(prop.getKey(), prop);

    }

    public interface QueryContextProperty{

            String getKey();
            Object getValue();

            Class getType();

    }

    public class IntContextProperty implements QueryContextProperty{

            private String key;
            private int value;

            public IntContextProperty(String key, int value) {

                    this.key = key;
                    this.value = value;

            }



            @Override
            public String getKey() {

                    return key;

            }

            @Override
            public Object getValue() {

                    return value;

            }

            @Override
            public Class getType() {

                    return IntContextProperty.class;

            }




    }

    public class LongContextProperty implements QueryContextProperty{

            private String key;
            private long value;

            public LongContextProperty(String key, long value) {

                    this.key = key;
                    this.value = value;

            }


            @Override
            public String getKey() {

                    return key;

            }

            @Override
            public Object getValue() {

                    return value;

            }

            @Override
            public Class getType() {

                    return LongContextProperty.class;

            }

    }

    public class StringContextProperty implements QueryContextProperty{

            private String key,value;

            public StringContextProperty(String key, String value) {

                    this.key = key;
                    this.value = value;

            }

            @Override
            public String getKey() {

                    return this.key;

            }

            @Override
            public Object getValue() {

                    return this.value;

            }

            @Override
            public Class getType() {

                    return QueryContext.class;

            }

    }

    public class BooleanContextProperty implements QueryContextProperty{

            private String key;
            private Boolean value;

            public BooleanContextProperty(String key, Boolean value) {

                    this.key = key;
                    this.value = value;

            }

            @Override
            public String getKey() {

                    return this.key;

            }

            @Override
            public Object getValue() {

                    return this.value;

            }

            @Override
            public Class getType() {

                    return BooleanContextProperty.class;

            }

    }


}
