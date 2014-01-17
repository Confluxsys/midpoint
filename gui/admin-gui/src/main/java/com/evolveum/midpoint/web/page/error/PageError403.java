package com.evolveum.midpoint.web.page.error;

import com.evolveum.midpoint.web.application.PageDescriptor;

/**
 * @author lazyman
 */
@PageDescriptor(url = "/error/403")
public class PageError403 extends PageError {

    public PageError403() {
        super(403);
    }
}
