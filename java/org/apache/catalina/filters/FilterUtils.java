package org.apache.catalina.filters;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class FilterUtils {
   /**
    * 
    * @param context
    * @param pathFilter the path that activates this filter
    * @throws ClassNotFoundException
    */
   public static void addHealthFilter(Context context, Wrapper w, String pathFilter) {
      // add our metrics filter
      FilterDef def = new FilterDef();
      def.setFilterClass(HealthFilter.class.getName());
      def.setFilterName("healthFilter");
      def.addInitParameter(HealthFilter.PATH_HEALTH, pathFilter);
      context.addFilterDef(def);
      FilterMap map = new FilterMap();
      map.setFilterName("healthFilter");
      map.addURLPattern("/*");
      context.addFilterMap(map);
      w.addMapping(pathFilter);
   }

   /**
    * 
    * @param context
    * @param pathFilter the path that activates this filter
    * @throws ClassNotFoundException
    */
   public static void addMetricsFilter(Context context, Wrapper w, String pathFilter) {
      // Código para añadir filtros

      FilterDef def = new FilterDef();
      def.setFilterClass(MetricsFilter.class.getName());
      def.setFilterName("metricsFilter");
      def.addInitParameter(MetricsFilter.PATH_METRICS, pathFilter);
      context.addFilterDef(def);
      FilterMap map = new FilterMap();
      map.setFilterName("metricsFilter");
      map.addURLPattern("/*");
      context.addFilterMap(map);
      w.addMapping(pathFilter);

   }

   /**
    * 
    * @param context
    * @param pathFilter the path that activates this filter
    * @throws ClassNotFoundException
    */
   public static void addTokenAuthFilter(Context context, String tokenName, String tokenValue, String urlPattern) {
      // Código para añadir filtros
      FilterDef def = new FilterDef();
      def.setFilterClass(TokenAuthFilter.class.getName());
      def.setFilterName("tokenAuthFilter");
      def.addInitParameter(TokenAuthFilter.TOKEN_NAME, tokenName);
      def.addInitParameter(TokenAuthFilter.TOKEN_VALUE, tokenValue);
      context.addFilterDef(def);

      FilterMap map = new FilterMap();
      map.setFilterName("tokenAuthFilter");
      map.addURLPattern(urlPattern);
      context.addFilterMap(map);
   }
}
