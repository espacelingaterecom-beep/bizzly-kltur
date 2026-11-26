# Security Summary

**Reviewed Files**: 28
**Issues Resolved**: 8 (plus 1 external server follow-up)

### Issues Resolved
| Area | File | Line | What Was Happening | Impact | Status | How It Was Fixed |
|---|---|---|---|---|---|---|
| Embedded Admin Setup Secret | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt`, `PinSecurity.kt` | 3030 | The KLTUR Admin first-run setup code was compared against a plaintext string literal in client Kotlin code, making it recoverable from a decompiled APK and lacking attempt throttling. | High | Fixed | Replaced the plaintext check with a salted PBKDF2-HMAC-SHA256 verification (`PinSecurity.verifyAdminInstallCode`) offloaded to a background thread and enforced exponential lockout throttling via `LockoutTracker("admin:install")`. |
| Missing Role & PIN Authorization Checks | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt` | 2191, 2318, 2377, 2528, 2613, 2853 | Business profile/branding updates, adding profiles, changing roles (including escalating to Manager), disabling cloud sync, and printing final invoices/receipts could be invoked without verifying role permissions or manager PIN authorization. | High | Fixed | Added `requirePermission` / `requireSensitivePin` checks matching each action's sensitivity, validated role keys against `BizzlyRole.entries`, and gated admin mutation entry points behind `admin.isAuthenticated`. |
| Unthrottled Admin Key Update | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt` | 3176 | `updateAdminPasscode` verified the existing admin key without consulting or updating the brute-force lockout counter. | Medium | Fixed | Wired `updateAdminPasscode` to `LockoutTracker("admin:kltur")` and required an active authenticated admin session. |
| Spreadsheet Formula Injection | `app/src/main/java/com/example/bizzly/util/ExportUtils.kt` | 1201, 1217 | Text cells in `.xlsx` exports (product names, movement labels, supplier names) were XML-escaped but kept leading `=`, `+`, `-`, `@`, `\t`, or `\r` characters that spreadsheet apps can interpret as formulas. | Medium | Fixed | Added `sanitizeSpreadsheetCell` to prefix dangerous leading characters with `'` before XML escaping (`&apos;`) while leaving numeric `<v>` cells untouched. |
| Unbounded File / Network Reads & Image OOM | `app/src/main/java/com/example/bizzly/data/repository/BizzlyRepository.kt`, `SupabaseSyncClient.kt`, `ExportUtils.kt` | 256, 206, 76 | Backup imports and HTTP responses read entire streams unconditionally into memory, and logo decoding loaded full camera resolution bitmaps before resizing. | Medium | Fixed | Added `readStreamBoundedUtf8` capping backup files at 10 MB and sync responses at 2–16 MB, plus two-pass `inJustDecodeBounds` / `inSampleSize` bitmap subsampling in `decodeAndResizeLogo`. |
| Out-of-Order Persistence & Incomplete Table Sync | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt`, `SupabaseSyncClient.kt` | 1053, 167, 775 | Parallel Room write coroutines could finish out of order under rapid taps, and `tb/<id>` sync documents omitted comped items, discounts, and partial split payments. | Medium | Fixed | Serialized Room mutations with `persistMutex` and included comped items, discount fields, and split payment totals in `tb/` sync payloads backward-compatibly. |
| Permissive Admin WebView Navigation | `app/src/main/java/com/example/bizzly/ui/screens/AdminScreen.kt` | 739 | The Netlify Admin `WebView` allowed navigation to any `https://` origin while JavaScript and DOM storage were enabled, and retained cached pages on exit. | Medium | Fixed | Restricted `shouldOverrideUrlLoading` to HTTPS URLs on the configured host/subdomains, disabled form saving and file URL access, and cleared cache/history on dispose. |
| Clipboard Preview Exposure & Main-Thread Write | `app/src/main/java/com/example/bizzly/util/ExportUtils.kt`, `MoreScreens.kt`, `AdminDialogs.kt` | 1381, 991, 1213 | Copying the 20-character cloud sync key did not mark the clipboard item as sensitive on Android 13+, and Document backup saving wrote bytes on the UI thread. | Low | Fixed | Added `ExportUtils.copySensitiveToClipboard` (`ClipDescription.EXTRA_IS_SENSITIVE`), moved Document backup I/O to `Dispatchers.IO`, and normalized remaining secondary buttons to `48.dp`. |
| External Backend RLS & RPC Policies | Supabase & Netlify Backend (external) | — | The app uses Supabase's public publishable client key and relies on Postgres Row-Level Security, `SECURITY DEFINER` RPC validation (`bz_create`, `bz_push`, `bz_pull`, `bz_admin_list`, `bz_spaces`), and Netlify Admin auth outside this Android repo. | Medium | Needs Follow-Up | Verify in the Supabase dashboard that RLS restricts direct table reads/writes (`bz_spaces` etc.), `bz_admin_list` checks an admin role/email claim, and RPCs validate `p_key` server-side. |

### Safeguards Verified
| Area | File | How It's Protected |
|---|---|---|
| PIN & Credential Cryptography | `app/src/main/java/com/example/bizzly/security/PinSecurity.kt` | Uses PBKDF2-HMAC-SHA256 (`150,000` iterations, 256-bit key, 18-char `SecureRandom` salt) with constant-time `MessageDigest.isEqual` verification and automatic upgrade from legacy v1 SHA-256 PINs on login. |
| Brute-Force & Inactivity Lock | `app/src/main/java/com/example/bizzly/security/PinSecurity.kt`, `BizzlyViewModel.kt` | Exponentially throttles logins, PIN overlays, and admin logins after 5 failed attempts, and locks active sessions automatically after 15 minutes of inactivity. |
| Local Database Queries & Hygiene | `app/src/main/java/com/example/bizzly/data/local/InventoryDao.kt`, `BizzlyDao.kt` | All Room queries are compile-time SQLite statements with bound parameters, safe `sanitizeAccount`/`sanitizeData` bounds, and `android:allowBackup="false"`. |
| Scoped File Sharing & Permissions | `app/src/main/AndroidManifest.xml`, `file_paths.xml` | Uses Android Photo Picker (`PickVisualMedia`, zero broad media/storage permissions), `FileProvider` restricted to `cache/exports`, `BLUETOOTH_SCAN` marked `neverForLocation`, and runtime permission flows for Bluetooth and `POST_NOTIFICATIONS`. |
| Transport Security | `app/build.gradle.kts`, `BizzlyRepository.kt` | Enforces HTTPS endpoints for Supabase (`BuildConfig.SUPABASE_URL`) and the Netlify portal (`isValidHttpsUrl`). |

## PoC Verification

### 1. Embedded Admin Setup Secret Recovery
#### Summary
| Field | Value |
|---|---|
| Issue Type | Exposed Hardcoded Credential |
| Impact | High |
| Affected File | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt:3030`, `PinSecurity.kt:85` |
| Test Scenario | Attempt repeated invalid install codes or inspect compiled bytecode for the raw setup code, then enter the valid setup code `SETUP-VBZR-9AF0-NW4M-DQG2`. |

#### How It Was Fixed
Removed the literal install code string from Kotlin source/bytecode, verifying input against a salted PBKDF2-HMAC-SHA256 digest (`ADMIN_INSTALL_SALT` + `ADMIN_INSTALL_HASH`) inside `PinSecurity.verifyAdminInstallCode`, backed by `LockoutTracker("admin:install")`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Caller invokes `setupAdminPasscode(installCode, code, confirm)` | Enters handler |
| 2 | `lockout.checkWaitMessage("admin:install")` checks whether 5 failed attempts triggered an exponential cooldown | **Blocks brute-force attempts** |
| 3 | `PinSecurity.verifyAdminInstallCode(installCode)` derives PBKDF2-HMAC-SHA256 (`150,000` rounds) on `Dispatchers.Default` and compares in constant time | Accepts valid code without storing plaintext; rejects invalid input and increments failure counter |

#### Conclusion
**Fix Verified** — Verified by unit test `hashedAdminInstallCodeVerifiesWithoutPlaintextLiteral` in `BizzlyCoreTests.kt`.

---

### 2. Missing Role & PIN Authorization on Profile Role Change & Cloud Disable
#### Summary
| Field | Value |
|---|---|
| Issue Type | Missing Authorization & Role Validation |
| Impact | High |
| Affected File | `app/src/main/java/com/example/bizzly/ui/viewmodel/BizzlyViewModel.kt:2318, 2377, 2853` |
| Test Scenario | Call `changeProfileRole(serverId, "gerant")` or `disableCloudSync()` while signed in as a non-manager (`SERVEUR`) without manager PIN approval. |

#### How It Was Fixed
Added `requireSensitivePin(BizzlyPermissions.RESET)` before mutating profile roles, adding profiles, or disabling sync, plus strict role allowlist validation (`BizzlyRole.entries`).

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Non-manager session triggers `changeProfileRole` or `disableCloudSync` | Enters guarded ViewModel method |
| 2 | `requireSensitivePin(BizzlyPermissions.RESET)` checks `oneShotAuthorized`; if false, opens `PinOverlayState` requiring a manager PIN | **Blocks unverified execution** |
| 3 | After manager PIN verification, `BizzlyRole.entries.firstOrNull { it.key == newRole }` rejects arbitrary role strings | **Safe role change only after manager PIN** |

#### Conclusion
**Fix Verified** — Privilege escalation and critical configuration actions now require manager role authorization and PIN confirmation.

---

### 3. Spreadsheet Formula Injection in Excel Exports
#### Summary
| Field | Value |
|---|---|
| Issue Type | Output Encoding / Formula Injection |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/bizzly/util/ExportUtils.kt:1201, 1217` |
| Test Scenario | Export `.xlsx` workbook containing a product named `=HYPERLINK("https://evil.test")` and a cash expense labeled `+1+1`. |

#### How It Was Fixed
Added `ExportUtils.sanitizeSpreadsheetCell` inside `buildXlsxZip` so string cells starting with `=`, `+`, `-`, `@`, `\t`, or `\r` are prefixed with `'` prior to XML escaping (`&apos;`).

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | `generateExcelBytes` / `generateRestockOrderExcel` passes cell values into `buildXlsxZip` | Reaches inline string branch |
| 2 | `sanitizeSpreadsheetCell(v.toString())` detects leading `=` / `+` and prepends `'` | Neutralizes formula trigger |
| 3 | `xesc(s)` writes `&apos;=HYPERLINK(...)` inside `<is><t>` | **Rendered safely as literal text by Excel/Sheets** |

#### Conclusion
**Fix Verified** — Verified by `xlsxExportNeutralizesSpreadsheetFormulaPayloads` in `BizzlyCoreTests.kt`.

---

### 4. Unbounded Backup File Read (Memory Exhaustion)
#### Summary
| Field | Value |
|---|---|
| Issue Type | Unbounded Resource Consumption |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/bizzly/data/repository/BizzlyRepository.kt:259`, `BizzlyViewModel.kt:785, 2450` |
| Test Scenario | Import a backup stream exceeding the byte limit (`MAX_BACKUP_BYTES` / test cap of 64 bytes). |

#### How It Was Fixed
Replaced unbounded `bufferedReader().readText()` calls with `BizzlyRepository.readStreamBoundedUtf8`, which stops reading and returns `null` as soon as cumulative bytes exceed the threshold.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | User picks a file in `importBackupFromAuth` or `restoreBackupInSession` | Opens `InputStream` on `Dispatchers.IO` |
| 2 | `readStreamBoundedUtf8` loops in 8 KB chunks tracking `total` | Stops immediately once `total > maxBytes` and returns `null` |
| 3 | ViewModel sees `oversized == true`, aborts JSON parsing, and shows toast `"Fichier trop volumineux (max 10 Mo)."` | **Handled safely without OOM** |

#### Conclusion
**Fix Verified** — Verified by `boundedStreamReaderRejectsOversizedBackup` in `BizzlyCoreTests.kt`.

---

### 5. Permissive Admin WebView Navigation
#### Summary
| Field | Value |
|---|---|
| Issue Type | Untrusted Origin Navigation in WebView |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/bizzly/ui/screens/AdminScreen.kt:739` |
| Test Scenario | A link inside the integrated Netlify WebView attempts to redirect to `https://untrusted.example/phish` or `file:///data/...`. |

#### How It Was Fixed
Disabled file/universal URL access and form caching, cleared WebView history/cache on exit, and constrained `shouldOverrideUrlLoading` to HTTPS URLs on `allowedHost` or its subdomains.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | WebView intercepts navigation request in `shouldOverrideUrlLoading` | Inspects `request.url` |
| 2 | Checks `scheme == "https"` and `targetHost == allowedHost || targetHost.endsWith(".$allowedHost")` | Returns `true` (blocked) for external hosts and non-HTTPS schemes |
| 3 | Leaving Admin invokes `DisposableEffect` `onDispose` | **Stops loading, clears cache/history, and destroys WebView** |

#### Conclusion
**Fix Verified** — External or local-file navigation cannot execute inside the privileged Admin WebView.
