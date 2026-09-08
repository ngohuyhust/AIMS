package vn.aims.integration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.aims.common.config.ProductionConfiguration;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTest {
    MockEnvironment valid() {return new MockEnvironment().withProperty("AIMS_DB_URL","jdbc:postgresql://db:5432/aims").withProperty("AIMS_DB_USERNAME","aims").withProperty("AIMS_DB_PASSWORD","synthetic").withProperty("APP_PUBLIC_URL","https://shop.example.test");}
    @Test void requiresExplicitDatabaseAndPublicUrl() {
        for(String key:new String[]{"AIMS_DB_URL","AIMS_DB_USERNAME","AIMS_DB_PASSWORD","APP_PUBLIC_URL"}) {var env=valid().withProperty(key,"");assertThatThrownBy(()->ProductionConfiguration.validate(env)).hasMessageContaining(key);}
        assertThatCode(()->ProductionConfiguration.validate(valid())).doesNotThrowAnyException();
    }
    @Test void refusesInsecurePublicUrlAndSandboxTrigger() {
        assertThatThrownBy(()->ProductionConfiguration.validate(valid().withProperty("APP_PUBLIC_URL","http://localhost:4200"))).hasMessageContaining("HTTPS");
        assertThatThrownBy(()->ProductionConfiguration.validate(valid().withProperty("VIETQR_ENABLE_TEST_CALLBACK","true"))).hasMessageContaining("VIETQR_ENABLE_TEST_CALLBACK");
    }
}
