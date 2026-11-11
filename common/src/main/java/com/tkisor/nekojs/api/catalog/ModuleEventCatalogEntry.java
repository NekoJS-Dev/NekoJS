package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;

/** One environment-filtered event publication, separate from legacy globals. */
public record ModuleEventCatalogEntry(String moduleId, String owner, BindingExposure.Category category,
                                      ScriptType scriptType, EventCatalogEntry event) {}
