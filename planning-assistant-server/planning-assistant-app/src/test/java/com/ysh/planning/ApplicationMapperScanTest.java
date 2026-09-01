package com.ysh.planning;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationMapperScanTest {

    @Test
    void scansOnlyInterfacesExplicitlyMarkedAsMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertEquals(Mapper.class, mapperScan.annotationClass());
    }
}
