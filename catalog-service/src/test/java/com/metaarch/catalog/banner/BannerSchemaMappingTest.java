package com.metaarch.catalog.banner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

class BannerSchemaMappingTest {
  @Test
  void enumColumnsRemainVarcharToMatchTheCatalogMigrationSchema() throws Exception {
    assertVarcharEnumColumn("scope");
    assertVarcharEnumColumn("status");
  }

  private void assertVarcharEnumColumn(String fieldName) throws Exception {
    var field = Banner.class.getDeclaredField(fieldName);
    assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
    var jdbcType = field.getAnnotation(JdbcTypeCode.class);
    assertNotNull(jdbcType, fieldName + " must pin its JDBC type to the VARCHAR schema");
    assertEquals(SqlTypes.VARCHAR, jdbcType.value());
  }
}
