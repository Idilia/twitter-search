package com.idilia.samples.ts.db;

import java.io.IOException;
import java.util.List;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idilia.tagging.Sense;


/**
 * Converter for serializing a list of senses as a string for storing
 * in the database and for recovering it.
 *
 * Implementation: Hibernate has a bug (HHH-8804) that prevents
 * using List<Sense> as the first argument of the template.
 */
@SuppressWarnings("rawtypes")
@Converter
public class SensesConverter implements AttributeConverter<List, String> {
//public class SensesConverter implements AttributeConverter<List<Sense>, String> {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  
  @Override
  public String convertToDatabaseColumn(List senses) {
    if (senses == null)
      return null;
    
    @SuppressWarnings("unchecked")
    List<Sense> s = (List<Sense>) senses;
    
    // Serialize using JSON
    try {
      return mapper.writeValueAsString(s);
    } catch (IOException e) {
      logger.error("Failed to save List<Sense> {}", senses.toString(), e);
      return null;
    }
  }

  @Override
  public List<Sense> convertToEntityAttribute(String dbData) {
    if (dbData == null)
      return null;
    
    try {
      return mapper.readValue(dbData, new TypeReference<List<Sense>>(){});
    } catch (IOException e) {
      logger.error("Failed to recover List<Sense> from {}", dbData.toString(), e);
      return null;
    }
  }
}
