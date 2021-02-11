package com.hbi.customerretention.service;

import com.hbi.customerretention.config.Config;
import com.hbi.customerretention.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import javax.xml.soap.Name;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

/**
 * <p>
 * Service class fetches input data invokes soap
 * webservice call to 3rd party system using webservice template and thereby
 * receives the response and translates to the message processor class.
 * </p>
 *
 * @author Bhanumitra Jena
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiebelService {

    private final Config config;
    private final WebServiceTemplate webServiceTemplate;

    @Retryable(value = {ServiceException.class}, maxAttempts = 4, backoff = @Backoff(delay = 5000))
    public Object process(Input input)
        throws ServiceException {
        try {
            log.info("siebel webservice method invocation start");
            log.debug("processing new message data into siebel format : {}", input);
            Object object = (Object) webServiceTemplate
                .marshalSendAndReceive(config.getEndPointUrl(), input,
                    (msg) -> constructHeaderMessage(msg,
                        config.getDigitalCardEnrollmentConfig().getSoapAction()));
            log.info("webservice method invocation ended successfull with result");
            return object;
        } catch (WebServiceIOException | SoapFaultClientException servicetException) {
            log.error("error processing the message {}",
                input.getCardNumber(),
                servicetException);
            throw new ServiceException("service.card.exception",
                input.getCardNumber());
        } catch (Exception exception) {
            log.error("write to the kafka for error {}",
                input.getCardNumber(),
                exception);
            throw new SiebelServiceException("service.card.exception",
                input.getCardNumber());
        }
    }

  
    private void constructHeaderMessage(WebServiceMessage message, String soapAction) {
        try {
            SaajSoapMessage saajSoapMessage = (SaajSoapMessage) message;
            saajSoapMessage.setSoapAction(soapAction);
            customHeader(saajSoapMessage);
        } catch (SOAPException soapException) {
            log.error("There was error {} in service invocation", message, soapException);
            throw new ServiceException("There was an error in construction of header", soapException);
        }
    }

    private void customHeader(SaajSoapMessage saajSoapMessage) throws SOAPException {
        SOAPMessage soapMessageXml = saajSoapMessage.getSaajMessage();
        SOAPPart soapPart = soapMessageXml.getSOAPPart();
        SOAPEnvelope soapEnvelope = soapPart.getEnvelope();
        SOAPHeader soapHeader = soapEnvelope.getHeader();
        SOAPHeaderElement soapHeaderElement = serviceHeader(soapEnvelope, soapHeader);
        soapHeaderElement.setMustUnderstand(false);
        soapMessageXml.saveChanges();
    }

    private SOAPHeaderElement serviceHeader(SOAPEnvelope soapEnvelope, SOAPHeader soapHeader)
        throws SOAPException {
        Name headerElementName = soapEnvelope.createName(config.getServiceSecurityText(),
            siebelConfig.getSiebelServiceSecurityName(), config.getWebsecurityname());
        SOAPHeaderElement soapHeaderElement = soapHeader.addHeaderElement(headerElementName);
        SOAPElement usernameTokenSoapElement = soapHeaderElement
            .addChildElement(config.getSiebelServiceTokenText(), config.getServiceSecurityName());
        SOAPElement userNameSoapElement = usernameTokenSoapElement.addChildElement(
        		config.getServiceUserNameText(), config.getServiceSecurityName());
        userNameSoapElement.addTextNode(config.getServiceUserName());
        SOAPElement passwordSoapElement = usernameTokenSoapElement.addChildElement(
        		config.getServicePasswordText(), config.getServiceSecurityName());
        passwordSoapElement.addTextNode(config.getServicePassword());
        return soapHeaderElement;
    }

}
