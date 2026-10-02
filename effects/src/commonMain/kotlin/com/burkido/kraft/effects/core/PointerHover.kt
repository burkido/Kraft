package com.burkido.kraft.effects.core

/**
 * Whether the platform's primary pointer can hover — the counterpart of CSS `(hover: hover)`:
 * true for a desktop mouse, false for touch screens, where there is no hover to start anything.
 */
expect val primaryPointerCanHover: Boolean
