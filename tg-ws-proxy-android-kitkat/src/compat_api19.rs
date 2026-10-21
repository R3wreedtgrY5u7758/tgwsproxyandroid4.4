//! Совместимость с Android 4.4 (API 19) на 32-битном ARM.
//!
//! В libc Android 4.4 нет двух функций, которые нативная библиотека
//! импортирует как *обычные* (не weak) символы:
//!
//!   * `epoll_create1`   — появилась в API 21 (её зовёт mio/tokio);
//!   * `dl_iterate_phdr` — на ARM доступна только с API 21 (её зовёт std).
//!
//! Без них `System.loadLibrary("tgwsproxy")` падает на 4.4 с
//! `cannot locate symbol "epoll_create1"`.
//!
//! Решение: определить эти функции прямо в нашей .so. На устройствах с
//! API >= 21 динамический линкер находит «настоящие» функции libc раньше
//! (libc — в глобальной области видимости), на 4.4 libc их не содержит и
//! используются наши реализации. Семантика идентична: это тонкие обёртки
//! над теми же системными вызовами ядра.
//!
//! Модуль компилируется ТОЛЬКО для 32-битного ARM Android.

#![cfg(all(target_os = "android", target_arch = "arm"))]
#![allow(non_snake_case)]

use std::os::raw::{c_int, c_long, c_void};

extern "C" {
    // libc: long syscall(long number, ...); выставляет errno при ошибке.
    fn syscall(number: c_long, ...) -> c_long;
}

/// Номер системного вызова epoll_create1 для ARM EABI (см. asm/unistd.h).
const SYS_EPOLL_CREATE1: c_long = 357;

#[no_mangle]
pub unsafe extern "C" fn epoll_create1(flags: c_int) -> c_int {
    syscall(SYS_EPOLL_CREATE1, flags as c_long) as c_int
}

/// Заглушка: «ни одного загруженного объекта не обошли».
///
/// Нужна только для раскрутки стека/бэктрейсов, а проект собирается с
/// `panic = "abort"`, поэтому на работу прокси это не влияет.
#[no_mangle]
pub unsafe extern "C" fn dl_iterate_phdr(
    _callback: Option<unsafe extern "C" fn(*mut c_void, usize, *mut c_void) -> c_int>,
    _data: *mut c_void,
) -> c_int {
    0
}
