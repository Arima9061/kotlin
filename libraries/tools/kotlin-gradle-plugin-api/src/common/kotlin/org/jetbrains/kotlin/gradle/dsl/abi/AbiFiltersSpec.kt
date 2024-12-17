/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl.abi

import org.gradle.api.Action
import org.gradle.api.provider.SetProperty

/**
 * Set of filtering rules that restrict ABI declarations included into a dump.
 *
 * It consists of a combination of rules for including and excluding declarations.
 * Each filter can be written as a filter for the class name (see [AbiFilterSetSpec.classes]), or an annotation filter (see [AbiFilterSetSpec.annotatedWith]).
 *
 * ```kotlin
 * filters {
 *     excluded {
 *         classes.add("foo.Bar")
 *         annotatedWith.add("foo.ExcludeAbi")
 *     }
 *
 *     included {
 *         classes.add("foo.api.**")
 *         annotatedWith.add("foo.PublicApi")
 *     }
 * }
 * ```
 *
 * In order for a declaration (class, field, property or function) to get into the dump, it must pass the inclusion **and** exclusion filters.
 *
 * Declaration passes the exclusion filter if it does not match any of class name (see [AbiFilterSetSpec.classes]) or annotation  (see [AbiFilterSetSpec.annotatedWith]) filter rule.
 *
 * Declaration passes the inclusion filters if there is no inclusion rules, or it matches any inclusion rule, or at least one of its members (actual for class declaration) matches inclusion rule.
 *
 * @since 2.1.20
 */
@AbiValidationDsl
@ExperimentalAbiValidation
public interface AbiFiltersSpec {
    val excluded: AbiFilterSetSpec
    val included: AbiFilterSetSpec

    /**
     * Configures the [excluded] with the provided configuration.
     */
    fun excluded(action: Action<AbiFilterSetSpec>) {
        action.execute(excluded)
    }

    /**
     * Configures the [included] with the provided configuration.
     */
    fun included(action: Action<AbiFilterSetSpec>) {
        action.execute(included)
    }
}

/**
 * Set of filters in one direction: inclusions or exclusions.
 *
 * Inclusion filters:
 * ```kotlin
 * filters {
 *     included {
 *         classes.add("foo.api.**")
 *         annotatedWith.add("foo.PublicApi")
 *     }
 * }
 *
 * Exclusion filters:
 * ```kotlin
 * filters {
 *     excluded {
 *         classes.add("foo.Bar")
 *         annotatedWith.add("foo.ExcludeAbi")
 *     }
 * }
 * ```
 *
 * @since 2.1.20
 */
@AbiValidationDsl
@ExperimentalAbiValidation
public interface AbiFilterSetSpec {
    /**
     * Filtering by a class name.
     *
     * The name filter works by comparing qualified class name with the value in the filter.
     * ```kotlin
     * filters {
     *     excluded {
     *         classes.add("foo.Bar") // name filter, excludes class with name `foo.Bar` from dump
     *     }
     * }
     * ```
     * For Kotlin classes, fully qualified names are used.
     * It is important to keep in mind that dots are used everywhere as separators, even in the case of a nested class.
     * E.g. for qualified name `foo.bar.Container.Value`, here `Value` is a class nested in `Container`.
     *
     * For classes from Java sources, canonical names are used.
     * The main motivation is a similar approach to writing the class name - dots are used everywhere as delimiters.
     *
     * It is allowed to use name templates, for this purpose wildcards `**`, `*` and `?` are added.
     * - `**` - zero or any amount of characters
     * - `*` - zero or any amount of characters excluding dot. Using to specify simple class name.
     * - `?` - any single character.
     *
     * ```kotlin
     * filters {
     *     excluded {
     *         classes.add("**.My*") // name filter, excludes class in any non-root package with name starting with `My`.
     *     }
     * }
     * ```
     */
    val classes: SetProperty<String>

    /**
     * Filtering by annotations placed on the declaration.
     *
     * If class or class member is annotated with one of specified annotation - then this declaration matches the filter.
     *
     * For exclusion filter it is means that class will be excluded from dump, for inclusion filter it will keep class or member in dump.
     *
     * Only qualified names of annotations can be used.
     * No wildcards are allowed.
     *
     * Example:
     * ```kotlin
     * filters {
     *     excluded {
     *         annotatedWith.add("foo.ExcludeAbi") // exclude any class property or function annotated with 'foo.ExcludeAbi'
     *     }
     * }
     * ```
     *
     * The annotation should not have [Retention] equal to [AnnotationRetention.SOURCE], otherwise, filtering by it will not work.
     *
     */
    val annotatedWith: SetProperty<String>
}
