package com.example.core_service;

public class RelationMeta {
    private String constraintName;
    private String fkTable;
    private String pkTable;
    private String fkColumn;
    private String pkColumn;

    public RelationMeta(String constraintName,
                        String fkTable,
                        String pkTable,
                        String fkColumn,
                        String pkColumn) {
        this.constraintName = constraintName;
        this.fkTable = fkTable;
        this.pkTable = pkTable;
        this.fkColumn = fkColumn;
        this.pkColumn = pkColumn;
    }

    public String getConstraintName() { return constraintName; }
    public String getFkTable() { return fkTable; }
    public String getPkTable() { return pkTable; }
    public String getFkColumn() { return fkColumn; }
    public String getPkColumn() { return pkColumn; }
}