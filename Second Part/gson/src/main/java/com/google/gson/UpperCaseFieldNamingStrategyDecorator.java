package com.google.gson;


import java.lang.reflect.Field;

class UpperCaseFieldNamingStrategyDecorator implements FieldNamingStrategy {

    private final FieldNamingStrategy delegate;

    public UpperCaseFieldNamingStrategyDecorator(FieldNamingStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public String translateName(Field f) {
        String fieldName = delegate.translateName(f);
        return fieldName.toUpperCase();
    }

}
