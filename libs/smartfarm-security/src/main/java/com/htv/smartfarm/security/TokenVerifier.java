package com.htv.smartfarm.security;

import com.htv.smartfarm.security.core.SecurityIdentity;

public interface TokenVerifier {

    SecurityIdentity verify(String bearerToken);
}
