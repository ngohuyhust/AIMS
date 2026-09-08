package vn.aims.common.config;

import java.net.URI;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@Profile("production")
public class ProductionConfiguration {
    public ProductionConfiguration(Environment environment) {validate(environment);}
    public static void validate(Environment environment) {
        for(String key:new String[]{"AIMS_DB_URL","AIMS_DB_USERNAME","AIMS_DB_PASSWORD","APP_PUBLIC_URL"})
            if(environment.getProperty(key,"").isBlank())throw new IllegalArgumentException("Production requires "+key);
        var url=URI.create(environment.getProperty("APP_PUBLIC_URL"));
        if(!"https".equals(url.getScheme()) || url.getHost()==null || url.getUserInfo()!=null || url.getQuery()!=null || url.getFragment()!=null)
            throw new IllegalArgumentException("Production APP_PUBLIC_URL requires HTTPS without credentials, query or fragment");
        if(environment.getProperty("VIETQR_ENABLE_TEST_CALLBACK",Boolean.class,false))throw new IllegalArgumentException("Production forbids VIETQR_ENABLE_TEST_CALLBACK");
    }
}
