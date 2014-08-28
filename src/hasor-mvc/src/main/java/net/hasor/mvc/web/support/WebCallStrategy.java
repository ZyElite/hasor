/*
 * Copyright 2008-2009 the original 赵永春(zyc@hasor.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hasor.mvc.web.support;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import net.hasor.core.Hasor;
import net.hasor.mvc.support.Call;
import net.hasor.mvc.support.CallStrategy;
import net.hasor.mvc.support.MappingInfo;
import net.hasor.mvc.web.restful.AttributeParam;
import net.hasor.mvc.web.restful.CookieParam;
import net.hasor.mvc.web.restful.HeaderParam;
import net.hasor.mvc.web.restful.PathParam;
import net.hasor.mvc.web.restful.Produces;
import net.hasor.mvc.web.restful.QueryParam;
import net.hasor.web.startup.RuntimeFilter;
import org.more.convert.ConverterUtils;
import org.more.util.BeanUtils;
import org.more.util.StringUtils;
/**
 * 
 * @version : 2014年8月27日
 * @author 赵永春(zyc@hasor.net)
 */
public class WebCallStrategy implements CallStrategy {
    //
    public final Object exeCall(Call call) throws Throwable {
        Object[] args = this.prepareParams(call);
        return this.returnCallBack(call.call(args), call);
    }
    /**处理 @Produces 注解。*/
    protected Object returnCallBack(Object returnData, Call call) {
        Method targetMethod = call.getMethod();
        if (targetMethod.isAnnotationPresent(Produces.class) == true) {
            Produces pro = targetMethod.getAnnotation(Produces.class);
            String proValue = pro.value();
            if (StringUtils.isBlank(proValue) == false) {
                RuntimeFilter.getLocalResponse().setContentType(proValue);
            }
        }
        return returnData;
    }
    //
    /**准备参数*/
    protected Object[] prepareParams(Call call) throws Throwable {
        MappingInfo mappingInfo = call.getMappingInfo();
        Method targetMethod = call.getMethod();
        //
        Class<?>[] targetParamClass = targetMethod.getParameterTypes();
        Annotation[][] targetParamAnno = targetMethod.getParameterAnnotations();
        targetParamClass = (targetParamClass == null) ? new Class<?>[0] : targetParamClass;
        targetParamAnno = (targetParamAnno == null) ? new Annotation[0][0] : targetParamAnno;
        ArrayList<Object> paramsArray = new ArrayList<Object>();
        /*准备参数*/
        for (int i = 0; i < targetParamClass.length; i++) {
            Class<?> paramClass = targetParamClass[i];
            Object paramObject = this.getIvnokeParams(paramClass, targetParamAnno[i], mappingInfo);//获取参数
            /*获取到的参数需要做一个类型转换，以防止method.invoke时发生异常。*/
            if (paramObject == null) {
                paramObject = BeanUtils.getDefaultValue(paramClass);
            } else {
                paramObject = ConverterUtils.convert(paramClass, paramObject);
            }
            paramsArray.add(paramObject);
        }
        Object[] invokeParams = paramsArray.toArray();
        return invokeParams;
    }
    /**/
    private Object getIvnokeParams(Class<?> paramClass, Annotation[] paramAnno, MappingInfo mappingInfo) {
        for (Annotation pAnno : paramAnno) {
            if (pAnno instanceof AttributeParam) {
                return this.getAttributeParam(paramClass, (AttributeParam) pAnno);
            } else if (pAnno instanceof CookieParam) {
                return this.getCookieParam(paramClass, (CookieParam) pAnno);
            } else if (pAnno instanceof HeaderParam) {
                return this.getHeaderParam(paramClass, (HeaderParam) pAnno);
            } else if (pAnno instanceof QueryParam) {
                return this.getQueryParam(paramClass, (QueryParam) pAnno);
            } else if (pAnno instanceof PathParam) {
                return this.getPathParam(paramClass, (PathParam) pAnno, mappingInfo);
            }
        }
        return BeanUtils.getDefaultValue(paramClass);
    }
    /**/
    private Object getPathParam(Class<?> paramClass, PathParam pAnno, MappingInfo mappingInfo) {
        String paramName = pAnno.value();
        return StringUtils.isBlank(paramName) ? null : this.getPathParamMap(mappingInfo).get(paramName);
    }
    /**/
    private Object getQueryParam(Class<?> paramClass, QueryParam pAnno) {
        String paramName = pAnno.value();
        return StringUtils.isBlank(paramName) ? null : this.getQueryParamMap().get(paramName);
    }
    /**/
    private Object getHeaderParam(Class<?> paramClass, HeaderParam pAnno) {
        String paramName = pAnno.value();
        if (StringUtils.isBlank(paramName)) {
            return null;
        }
        //
        HttpServletRequest httpRequest = RuntimeFilter.getLocalRequest();
        Enumeration<?> e = httpRequest.getHeaderNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement().toString();
            if (StringUtils.equalsIgnoreCase(name, paramName)) {
                ArrayList<Object> headerList = new ArrayList<Object>();
                Enumeration<?> v = httpRequest.getHeaders(paramName);
                while (v.hasMoreElements()) {
                    headerList.add(v.nextElement());
                }
                return headerList;
            }
        }
        return null;
    }
    /**/
    private Object getCookieParam(Class<?> paramClass, CookieParam pAnno) {
        String paramName = pAnno.value();
        if (StringUtils.isBlank(paramName)) {
            return null;
        }
        //
        HttpServletRequest httpRequest = RuntimeFilter.getLocalRequest();
        Cookie[] cookies = httpRequest.getCookies();
        ArrayList<String> cookieList = new ArrayList<String>();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (StringUtils.equalsIgnoreCase(cookie.getName(), paramName)) {
                    cookieList.add(cookie.getValue());
                }
            }
        }
        return cookieList;
    }
    /**/
    private Object getAttributeParam(Class<?> paramClass, AttributeParam pAnno) {
        String paramName = pAnno.value();
        if (StringUtils.isBlank(paramName)) {
            return null;
        }
        HttpServletRequest httpRequest = RuntimeFilter.getLocalRequest();
        Enumeration<?> e = httpRequest.getAttributeNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement().toString();
            if (StringUtils.equalsIgnoreCase(name, paramName)) {
                return httpRequest.getAttribute(paramName);
            }
        }
        return null;
    }
    /**/
    private Map<String, List<String>> queryParamLocal;
    private Map<String, List<String>> getQueryParamMap() {
        if (queryParamLocal != null) {
            return queryParamLocal;
        }
        //
        HttpServletRequest httpRequest = RuntimeFilter.getLocalRequest();
        String queryString = httpRequest.getQueryString();
        if (StringUtils.isBlank(queryString)) {
            return null;
        }
        //
        queryParamLocal = new HashMap<String, List<String>>();
        String[] params = queryString.split("&");
        for (String pData : params) {
            String oriData = null;
            String encoding = httpRequest.getCharacterEncoding();
            try {
                oriData = URLDecoder.decode(pData, encoding);
            } catch (Exception e) {
                Hasor.logWarn("use ‘%s’ decode ‘%s’ error.", encoding, pData);
                continue;
            }
            String[] kv = oriData.split("=");
            if (kv.length < 2) {
                continue;
            }
            String k = kv[0].trim().toUpperCase();
            String v = kv[1];
            //
            List<String> pArray = queryParamLocal.get(k);
            pArray = pArray == null ? new ArrayList<String>() : pArray;
            if (pArray.contains(v) == false) {
                pArray.add(v);
            }
            queryParamLocal.put(k, pArray);
        }
        return queryParamLocal;
    }
    /**/
    private Map<String, Object> pathParamsLocal;
    private Map<String, Object> getPathParamMap(MappingInfo mappingInfo) {
        if (pathParamsLocal != null) {
            return pathParamsLocal;
        }
        //
        HttpServletRequest httpRequest = RuntimeFilter.getLocalRequest();
        String requestPath = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        String matchVar = mappingInfo.getMappingToMatches();
        String matchKey = "(?:\\{(\\w+)\\}){1,}";//  (?:\{(\w+)\}){1,}
        Matcher keyM = Pattern.compile(matchKey).matcher(mappingInfo.getMappingTo());
        Matcher varM = Pattern.compile(matchVar).matcher(requestPath);
        ArrayList<String> keyArray = new ArrayList<String>();
        ArrayList<String> varArray = new ArrayList<String>();
        while (keyM.find()) {
            keyArray.add(keyM.group(1));
        }
        varM.find();
        for (int i = 1; i <= varM.groupCount(); i++) {
            varArray.add(varM.group(i));
        }
        //
        Map<String, List<String>> uriParams = new HashMap<String, List<String>>();
        for (int i = 0; i < keyArray.size(); i++) {
            String k = keyArray.get(i);
            String v = varArray.get(i);
            List<String> pArray = uriParams.get(k);
            pArray = pArray == null ? new ArrayList<String>() : pArray;
            if (pArray.contains(v) == false) {
                pArray.add(v);
            }
            uriParams.put(k, pArray);
        }
        pathParamsLocal = new HashMap<String, Object>();
        for (Entry<String, List<String>> ent : uriParams.entrySet()) {
            String k = ent.getKey();
            List<String> v = ent.getValue();
            pathParamsLocal.put(k, v.toArray(new String[v.size()]));
        }
        return pathParamsLocal;
    }
}