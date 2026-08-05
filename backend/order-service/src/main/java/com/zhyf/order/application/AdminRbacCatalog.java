package com.zhyf.order.application;

import java.util.List;

public record AdminRbacCatalog(
        List<AdminRbacPermissionOption> permissions,
        List<AdminRbacInstitutionOption> institutions
) {
}
