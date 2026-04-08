// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Application;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;
import dev.bscs.security.TrustManagerPolicy;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages creation of the {@link SSLContext} for the WebSockets to use. This configures itself from the following
 * {@link SCProperties} properties. For file-based configuration, these properties are known as "sc.xxx":
 *    ss.privateKey              either a PEM file name or hex encoding of the DER data
 *    ss.operationalCertificate  either a PEM file name or hex encoding of the DER data
 *    ss.caCertificates          a colon-separated concatenation of: either a PEM file name or hex encoding of the DER data
 *    ss.tlsVersion              either "TLSv1.2" or "TLSv1.3"
 *    ss.noValidation            set to true to have the TLS certificate validator ignore the peer's certificate
 * @author drobin
 */
public class SCTLSManager {

    private static final Log log = new Log(SCTLSManager.class);

    protected SCProperties properties;
    protected SSLContext   sslContext;

    public SCTLSManager(SCProperties properties) {
        this.properties = properties;
        try { this.sslContext = makeSSLContext(); }
        catch (Failure.Error e) {
            log.configuration("SSL configuration error: "+e);
            properties.tlsError = true;
            properties.tlsErrorClass  = e.errorClass;
            properties.tlsErrorCode   = e.errorCode;
            properties.tlsErrorReason = e.description;
        }
    }

    public  SSLContext getSSLContext() {
        return sslContext;
    }

    private SSLContext makeSSLContext() throws Failure.Error {
        byte[] deviceKeyBytes  = properties.privateKey.contains(".")?  loadPEM(properties.privateKey) : Formatting.fromHex(properties.privateKey);
        byte[] deviceCertBytes = properties.operationalCertificate.contains(".")? loadPEM(properties.operationalCertificate) : Formatting.fromHex(properties.operationalCertificate);
        List<byte[]> caCertsBytes = new ArrayList<>();
        String[] caCertStrings = properties.caCertificates.split(":");
        for (String caCertString: caCertStrings) { caCertsBytes.add(caCertString.contains(".")? loadPEM(caCertString) : Formatting.fromHex(caCertString)); }
        return makeSSLContext(deviceKeyBytes, deviceCertBytes, caCertsBytes, properties.noValidation);
    }

    private byte[] loadPEM(String fileName) {
        try {
            InputStream in = new FileInputStream(fileName);
            String pem = new String(readAllBytes(in), StandardCharsets.ISO_8859_1);
            if (!pem.startsWith("---")) { log.configuration("PEM file "+fileName+" contains extra stuff at start. use openssl -notext"); return new byte[0]; }
            Pattern parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
            String encoded = parse.matcher(pem).replaceFirst("$1");
            return Base64.getMimeDecoder().decode(encoded);
        }
        catch (IOException e) {
            log.configuration("Could not read PEM file \""+fileName+"\"");
            return new byte[0];
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException { // can be eliminated if java 9+
        ByteArrayOutputStream baos= new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        for (int read=0; read != -1; read = in.read(buf)) { baos.write(buf, 0, read); }
        return baos.toByteArray();
    }

    private SSLContext makeSSLContext(byte[] deviceKeyBytes, byte[] deviceCertBytes, List<byte[]> caCertsBytes, boolean noValidation) throws Failure.Error {
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(deviceKeyBytes);
            PrivateKey deviceKey;
            try {
                deviceKey=KeyFactory.getInstance("EC").generatePrivate(keySpec);
            }
            catch (InvalidKeySpecException e) {
                deviceKey=KeyFactory.getInstance("RSA").generatePrivate(keySpec);
            }
            log.info("Using Private Key "+" fmt="+deviceKey.getFormat()+" alg="+deviceKey.getAlgorithm());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate deviceCert = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(deviceCertBytes));
            X509Certificate[] caCerts  = new X509Certificate[caCertsBytes.size()];
            for (int i = 0; i< caCertsBytes.size(); i++) {
                caCerts[i] = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(caCertsBytes.get(i)));
            }

            KeyStore keystore = KeyStore.getInstance("JKS");
            try { keystore.load(null);} catch (IOException e) {} // can't happen with null
            keystore.setCertificateEntry("dev-cert", deviceCert);
            keystore.setKeyEntry("dev-key", deviceKey, "".toCharArray(), new Certificate[]{deviceCert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, "".toCharArray());
            KeyManager[] keyManagers = kmf.getKeyManagers();

            TrustManagerPolicy policy = null;
            if (!properties.policyClass.isEmpty()) {
                try {
                    policy = (TrustManagerPolicy)Class.forName(properties.policyClass).getDeclaredConstructor().newInstance();
                    policy.policyInitialize(Application.configuration,"sc");
                }
                catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    log.configuration("makeSSLContext: Can't find or instantiate class for policy '"+properties.policyClass+"'");
                }
            }
            TrustManager[] trustManagers = new TrustManager[] { makeTrustManager(caCerts,noValidation,policy) };

            SSLContext ctx = SSLContext.getInstance(properties.tlsVersion);
            ctx.init(keyManagers, trustManagers, null);
            return ctx;
        }
        catch (KeyStoreException e)         { throw new Failure.Error(ErrorClass.DEVICE,   ErrorCode.INTERNAL_ERROR,"Keystore error: %s",e.getMessage()); }
        catch (UnrecoverableKeyException e) { throw new Failure.Error(ErrorClass.DEVICE,   ErrorCode.INTERNAL_ERROR,"KeyManagerFactory error: %s",e.getMessage()); }
        catch (KeyManagementException e)    { throw new Failure.Error(ErrorClass.DEVICE,   ErrorCode.INTERNAL_ERROR,"SSLContext.init error: %s",e.getMessage()); }
        catch (CertificateException e)      { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.INVALID_CONFIGURATION_DATA,"Bad certificate data: %s",e.getMessage()); }
        catch (InvalidKeySpecException e)   { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.INVALID_CONFIGURATION_DATA,"Bad key data: %s",e.getMessage()); }
        catch (NoSuchAlgorithmException e)  { throw new Failure.Error(ErrorClass.DEVICE,   ErrorCode.INTERNAL_ERROR,"Algorithm not available: %s",e.getMessage()); }
    }

    private TrustManager makeTrustManager(X509Certificate[] caCerts, boolean noValidation, TrustManagerPolicy policy) {
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return noValidation? new X509Certificate[0] : caCerts;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException{
                if (policy != null && !policy.policyCheckClient(certs[0])) {
                    log.warn("TrustManager rejected client: {"+certs[0].getSubjectDN().getName()+"} by policy");
                    throw new CertificateException("Certificate not accepted by policy");
                }
                if (noValidation) log.info("TrustManager is blindly accepting client: { "+certs[0].getSubjectDN().getName()+"}");
                else {
                    log.info("TrustManager is checking out client: { "+certs[0].getSubjectDN().getName()+", signed with "+certs[0].getSigAlgName()+"}");
                    validateAgainstCAs(certs[0],caCerts);
                }
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                if (policy != null && !policy.policyCheckClient(certs[0])) {
                    log.warn("TrustManager rejected client: {"+certs[0].getSubjectDN().getName()+"} by policy");
                    throw new CertificateException("Certificate not accepted by policy");
                }
                if (noValidation) log.info("TrustManager is blindly accepting server: {"+certs[0].getSubjectDN().getName()+"}");
                else {
                    log.info("TrustManager is checking out server: {"+certs[0].getSubjectDN().getName()+", signed with "+certs[0].getSigAlgName()+"}");
                    validateAgainstCAs(certs[0],caCerts);
                }
            }};
    }

    private void validateAgainstCAs(X509Certificate cert, X509Certificate[] caCerts) throws CertificateException {
        StringBuilder errors = new StringBuilder();
        for (X509Certificate caCert : caCerts) {
            try {
                cert.checkValidity();
                cert.verify(caCert.getPublicKey());
                log.info("TrustManager approves of signature by: {"+caCert.getSubjectDN().getName()+", signed with "+caCert.getSigAlgName()+"}");
                return; // just return from here if any of the CAs check out
            }
            catch (Exception e) { errors.append("{"+cert.getSubjectDN().getName()+"}: "+e.getLocalizedMessage());}
        }
        log.error("Certificate validation error: "+errors.toString());
        throw new CertificateException("Certificate not accepted");
    }

}
