package com.engsoft2.currencyconversionservice;

import java.math.BigDecimal;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import java.util.function.Supplier;

@RestController
public class CurrencyConversionController {
    @Autowired
    private CurrencyExchangeProxy proxy;
	private CircuitBreakerFactory circuitBreakerFactory;

    public CurrencyConversionController(CircuitBreakerFactory cbFactory) {
        this.circuitBreakerFactory = cbFactory;
    }

    @GetMapping("/currency-conversion/from/{from}/to/{to}/quantity/{quantity}")
    public CurrencyConversion calculateCurrencyConversionController(@PathVariable String from, @PathVariable String to, @PathVariable BigDecimal quantity) {
        HashMap<String, String> uriVariables = new HashMap<>();
		uriVariables.put("from",from);
		uriVariables.put("to",to);

		return circuitBreakerFactory.create("calculateCurrencyConversion").run((java.util.function.Supplier<CurrencyConversion>) this.calculateCurrencyConversionSupplier(from, to, quantity), (Throwable t) -> {
            CurrencyConversion fallback = new CurrencyConversion(
                Long.valueOf(1), 
                from, to, BigDecimal.valueOf(1), 
                BigDecimal.valueOf(1), 
                BigDecimal.valueOf(1), 
                "rest template"
            );
			return fallback;
		});
    }

    @GetMapping("/currency-conversion-feign/from/{from}/to/{to}/quantity/{quantity}")
    public CurrencyConversion calculateCurrencyConversionFeignController(@PathVariable String from, @PathVariable String to, @PathVariable BigDecimal quantity) {
		return circuitBreakerFactory.create("calculateCurrencyConversionFeign").run((java.util.function.Supplier<CurrencyConversion>) this.calculateCurrencyConversionFeignSupplier(from, to, quantity), t -> {
            CurrencyConversion fallback = new CurrencyConversion(
                Long.valueOf(1), 
                from, to, BigDecimal.valueOf(1), 
                BigDecimal.valueOf(1), 
                BigDecimal.valueOf(1), 
                "feign"
            );
			return fallback;
		});
    }

	public CurrencyConversion calculateCurrencyConversion(String from, String to, BigDecimal quantity) {
        ResponseEntity<CurrencyConversion> responseEntity = new RestTemplate().getForEntity("http://localhost:8000/currency-exchange/from/{from}/to/{to}", CurrencyConversion.class, uriVariables);
        CurrencyConversion currencyConversion = responseEntity.getBody();
        return new CurrencyConversion(
            currencyConversion.getId(), 
			from, to, quantity, 
			currencyConversion.getConversionMultiple(), 
			quantity.multiply(currencyConversion.getConversionMultiple()), 
			currencyConversion.getEnvironment()+ " " + "rest template"
        );
	}

	public Supplier<CurrencyConversion> calculateCurrencyConversionSupplier(String from, String to, BigDecimal quantity) {
		return () -> this.calculateCurrencyConversion(from, to, quantity);
	}

	public CurrencyConversion calculateCurrencyConversionFeign(String from, String to, BigDecimal quantity) {
        CurrencyConversion currencyConversion = proxy.retrieveExchangeValue(from, to);
        return new CurrencyConversion(currencyConversion.getId(), 
			from, to, quantity, 
			currencyConversion.getConversionMultiple(), 
			quantity.multiply(currencyConversion.getConversionMultiple()), 
			currencyConversion.getEnvironment() + " " + "feign"
        );
	}

	public Supplier<CurrencyConversion> calculateCurrencyConversionFeignSupplier(String from, String to, BigDecimal quantity) {
		return () -> this.calculateCurrencyConversionFeign(from, to, quantity);
	}
}
