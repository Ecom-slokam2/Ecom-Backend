package com.metaarch.inventory.reservation;

import com.metaarch.sync.PublicApiSecurity;
import com.metaarch.sync.CatalogLocationClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({PublicApiSecurity.class, CatalogLocationClient.class})
public class InventorySecurity { }
