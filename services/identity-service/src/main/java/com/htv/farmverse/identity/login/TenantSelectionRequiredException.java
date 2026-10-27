package com.htv.farmverse.identity.login;

import org.springframework.security.authentication.BadCredentialsException;

/** Tài khoản thuộc nhiều tenant (hoặc mã tenant sai) nên phải chọn tenant khi đăng nhập. */
public class TenantSelectionRequiredException extends BadCredentialsException {

    public TenantSelectionRequiredException() {
        super("Tenant selection required");
    }
}
