package com.davidepetilli.fdl.plugin;

import java.util.List;

public class AstDefinition {
    String baseName;
    List<Type> types;

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public List<Type> getTypes() {
        return types;
    }

    public void setTypes(List<Type> types) {
        this.types = types;
    }

    public static class Type {
        String name;
        List<Field> values;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Field> getValues() {
            return values;
        }

        public void setValues(List<Field> values) {
            this.values = values;
        }

        static public class Field {
            String type;
            String name;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
    }
}
