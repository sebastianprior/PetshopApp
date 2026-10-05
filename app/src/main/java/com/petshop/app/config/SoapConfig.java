package com.petshop.app.config;

import com.petshop.app.payment.soap.AutorizarPagoRequest;
import com.petshop.app.payment.soap.AutorizarPagoResponse;
import com.petshop.app.payment.soap.ConsultarPagoRequest;
import com.petshop.app.payment.soap.ConsultarPagoResponse;
import com.petshop.app.payment.soap.GatewayFaultResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;

import java.time.Duration;

/** Servidor SOAP de la pasarela (/ws, WSDL en /ws/pagos.wsdl) y la plantilla con la que el checkout la consume. */
@Configuration
@EnableWs
public class SoapConfig {

    /** Servlet SOAP en /ws/* (WSDL en /ws/pagos.wsdl): convive con el DispatcherServlet de Spring MVC. */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        ServletRegistrationBean<MessageDispatcherServlet> registration = new ServletRegistrationBean<>(servlet, "/ws/*");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public SimpleWsdl11Definition pagos() {
        return new SimpleWsdl11Definition(new ClassPathResource("ws/pagos.wsdl"));
    }

    @Bean
    public GatewayFaultResolver gatewayFaultResolver() {
        GatewayFaultResolver resolver = new GatewayFaultResolver();
        resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return resolver;
    }

    @Bean
    public Jaxb2Marshaller pagosMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(AutorizarPagoRequest.class, AutorizarPagoResponse.class,
                ConsultarPagoRequest.class, ConsultarPagoResponse.class);
        return marshaller;
    }

    @Bean
    public WebServiceTemplate pagosWebServiceTemplate(WebServiceTemplateBuilder builder, Jaxb2Marshaller pagosMarshaller,
                                                      @Value("${petshop.payments.soap-url:http://localhost:${server.port:8080}/ws}") String url,
                                                      @Value("${petshop.payments.soap-read-timeout-ms:30000}") long readTimeoutMs) {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofSeconds(3));
        sender.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder
                .messageSenders(sender)
                .setMarshaller(pagosMarshaller)
                .setUnmarshaller(pagosMarshaller)
                .setDefaultUri(url)
                .build();
    }
}
