package vn.aims.vietqr;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/vqr")
public class VietqrMerchantController {
    private final VietqrService service;private final VietqrMerchantTokens tokens;
    public VietqrMerchantController(VietqrService service,VietqrMerchantTokens tokens) {this.service=service;this.tokens=tokens;}
    @PostMapping({"/api/token_generate","/api/token_generate/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object token(@RequestHeader(value="Authorization",required=false) String authorization) {return tokens.issue(authorization);}
    @PostMapping({"/bank/api/transaction-callback","/bank/api/transaction-callback/","/bank/api/transaction-sync","/bank/api/transaction-sync/"}) @ResponseStatus(HttpStatus.CREATED)
    public Object callback(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String authorization) {
        tokens.verify(authorization);var dto=JsonNodeFactory.instance.objectNode();
        for(String key:List.of("bankaccount","amount","transType","content","transactionid","transactiontime","referencenumber","orderId","terminalCode","sign")) {
            String alias=switch(key) {case "bankaccount"->"bankAccount";case "transactionid"->"transactionId";case "transactiontime"->"transactionTime";case "referencenumber"->"referenceNumber";case "orderId"->"orderid";default->key;};
            var value=body.get(key);if(value==null || value.isNull()) value=body.get(alias);
            if(value==null || value.isNull()) continue;
            if(key.equals("amount") || key.equals("transactiontime")) {
                try {dto.put(key,new java.math.BigDecimal(value.asText()));} catch(NumberFormatException e) {dto.set(key,value);}
            } else dto.put(key,value.asText());
        }
        return service.callback(VietqrInput.parse(dto,false),authorization);
    }
}
