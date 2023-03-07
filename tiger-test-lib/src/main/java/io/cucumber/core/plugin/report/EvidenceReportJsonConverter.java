package io.cucumber.core.plugin.report;

import java.util.Collection;
import org.apache.commons.lang3.ClassUtils;
import org.json.JSONArray;
import org.json.JSONObject;

 public class EvidenceReportJsonConverter {

   private EvidenceReportJsonConverter() {
     // Util
   }

   public static String toJson(Object object) {

     if (object == null) {
       return null;
     } else if (object instanceof CharSequence) {
       return object.toString();
     } else if (object instanceof Collection || object.getClass().isArray()) {
       return new JSONArray().putAll(object).toString(2);
     } else if (ClassUtils.isPrimitiveWrapper(object.getClass())) {
       return object.toString();
     } else {
       return new JSONObject(object).toString(2);
     }
   }
}
