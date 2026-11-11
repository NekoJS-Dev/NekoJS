package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.data.BindingExposure;

/** A module-only export derived from the same registration used at runtime. */
public record ModuleBindingCatalogEntry(String moduleId, String owner,
                                        BindingExposure.Category category, BindingCatalogEntry binding) {}
