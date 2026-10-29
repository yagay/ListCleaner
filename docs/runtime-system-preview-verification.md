# Runtime system-list verification architecture

This change separates three different truths that previous previews mixed:

1. **Registered candidates** are PackageManager or provider/launcher inventory used for
   editing ListCleaner rules. They must remain complete enough to restore filtered items.
2. **Actual framework return values** are read after the ListCleaner Hook executes inside
   DocumentsUI, RoleController, ShortcutService or a supported Android manager API.
   Returning an item to the caller does **not** prove it was drawn in the final UI.
3. **Confirmed enabled states** are only lists explicitly returned by dedicated enabled
   manager APIs (currently enabled input methods and accessibility services).

## Picker test correlation

A native picker launch records IntentKind, MIME/URI test scenario, Android user and
start timestamp *before* `startActivity`. On returning, the manager synchronizes
DocumentsUI Hook observations and checks them against the session.

- Same user + kind + scenario, a matching-roots Hook call after the launch and within
  a 5-minute window: **observed after this test**. These are returned matching roots,
  not a screenshot of the final list.
- A launched test with no new matching-roots Hook result: **awaiting Hook**; do not
  reuse a prior result as current.
- Different scenario/user, expired session or no explicit test: **historical only**,
  placed under the details disclosure.
- No snapshot: **not captured**, never render a fabricated list.

Because another app can invoke DocumentsUI within the same window, temporal
correlation is useful evidence but is deliberately **not** called UI verification.

## Runtime return snapshots by type

The typed, bounded `RuntimeInterfaceSnapshotCodec` is separate from the existing
observed-entry rule catalog and excludes file names, URIs, contacts, shortcut IDs and
contents. Writers run on a background executor, not in the hooked Binder call.

| Path | Captured value | Limitation |
| --- | --- | --- |
| RoleController | Package names in the hooked post-filter qualifying-packages return | Not installed role holders or pixels in OEM settings |
| Direct Share | Target package names from the post-filter ShortcutService share-target return | Not the ranked final Sharesheet; contact/shortcut identities are never stored |
| Accessibility/Print manager | Package names actually returned by hooked manager list methods | Not proof the OEM Settings UI drew all entries |
| Credential manager (retired) | No active ListCleaner scanner or filter; legacy serialized rules are read for compatibility only | Native Android credential providers are not modified or hidden by ListCleaner |
| DocumentsUI | Matching roots returned by its instrumented RootsCache method | OEM UI can still hide/change roots after the returned collection |

Other categories (PhotoPicker cloud provider, tiles, widgets, launchers, VPN, NFC,
ordinary share/resolver) still carry an explicit **candidate-only / not-public**
provenance. Their final host-controlled list has not been captured, and should not
be labeled enabled. No generic hook can prove every vendor-specific final panel.

## Safety / compatibility

- Previous rules, authorization and catalog scans are unchanged.
- System components still use established Hook logic; the new stream only mirrors
  post-filter package results, with no sensitive share text or file identifiers.
- Hook compatibility has changed; the new runtime capture code requires reloading
  the corresponding hooked processes after installing the new APK.
- No other Android user-space result is substituted for the selected one.
- Older snapshots may still be useful as diagnostics, but are never treated as
  current unless the test window is compatible.

## Device acceptance

1. Choose content (Any, text, image, PDF): launch native picker, return, and confirm
   whether DocumentsUI matching-roots was captured **after this test**. Changing MIME
   must not carry the previous snapshot as current.
2. Direct Share: launch a share from a supported app. Returned service packages appear
   only after the relevant Hook fires, separate from query candidates and contact data.
3. Browser/Home/Assistant/Call screening: enter the corresponding role settings and
   compare RoleController Hook-return packages with diagnostic candidates.
4. Input methods/accessibility: retain explicitly **enabled** service lists; manager
   Hook returns remain separately labeled evidence.
5. A missing Hook, OEM-specific interface or Android user profile must be shown as
   **not verified**, not an empty/disabled list.
