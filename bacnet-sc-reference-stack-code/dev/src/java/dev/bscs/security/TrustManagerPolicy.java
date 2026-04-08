// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.security;

import dev.bscs.common.Configuration;

import java.security.cert.X509Certificate;

/**
 * A "hook" to apply custom TLS policies without modifying core code.
 * For example, the config property sc.policyClass can set a class name for an implementer of this class that will be
 * called by SCTLSManager for incoming connections.
 * @author drobin
 */
public interface TrustManagerPolicy {
    void    policyInitialize(Configuration parameters, String prefix);
    boolean policyCheckClient(X509Certificate cert);
    boolean policyCheckServer(X509Certificate cert);
}
