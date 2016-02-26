package com.idilia.samples.ts.db;

import java.util.Arrays;
import java.util.List;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Converter for serializing a list of user keywords as a string for storing in
 * the database and for recovering it.
 *
 * Implementation: Hibernate has a bug (HHH-8804) that prevents using List
 * <String> as the first argument of the template.
 */
@SuppressWarnings("rawtypes")
@Converter
public class KeywordsConverter implements AttributeConverter<List, String> {
  // public class KeywordsConverter implements AttributeConverter<List<String>,
  // String> {

  @Override
  public String convertToDatabaseColumn(List keywords) {
    if (keywords == null || keywords.isEmpty())
      return null;

    @SuppressWarnings("unchecked")
    List<String> kws = (List<String>) keywords;

    // Convert to a tab-separated string of strings
    StringBuilder sb = new StringBuilder(kws.size() * 16);
    for (String kw : kws)
      sb.append(kw).append('\t');
    if (sb.length() > 0)
      sb.setLength(sb.length() - 1);
    return sb.toString();
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null)
      return null;
    return Arrays.asList(dbData.split("\t"));
  }
}
