package com.htv.farmverse.security;

import com.htv.farmverse.security.core.SecurityIdentity;

public interface TokenVerifier {

    SecurityIdentity verify(String bearerToken);
}
