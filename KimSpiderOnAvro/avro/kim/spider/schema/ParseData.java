/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package kim.spider.schema;

@SuppressWarnings("all")
public class ParseData extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = org.apache.avro.Schema.parse("{\"type\":\"record\",\"name\":\"ParseData\",\"namespace\":\"kim.spider.schema\",\"fields\":[{\"name\":\"outlinks\",\"type\":[\"null\",{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Outlink\",\"fields\":[{\"name\":\"url\",\"type\":\"string\"},{\"name\":\"anchor\",\"type\":\"string\"},{\"name\":\"fetchInterval\",\"type\":\"int\"},{\"name\":\"extend\",\"type\":{\"type\":\"map\",\"values\":\"string\"}}]}}]},{\"name\":\"extend\",\"type\":[\"null\",{\"type\":\"map\",\"values\":\"string\"}]}]}");
  public java.util.List<kim.spider.schema.Outlink> outlinks;
  public java.util.Map<java.lang.CharSequence,java.lang.CharSequence> extend;
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return outlinks;
    case 1: return extend;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: outlinks = (java.util.List<kim.spider.schema.Outlink>)value$; break;
    case 1: extend = (java.util.Map<java.lang.CharSequence,java.lang.CharSequence>)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
}
