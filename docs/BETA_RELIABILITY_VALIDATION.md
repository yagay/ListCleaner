# Beta Root, navigation and multi-space validation

## Root staged operations
- Mixed disable/enable: verified successes clear, partial failures and unknown states remain staged.
- Denied KernelSU access and cancellation never mark unfinished targets successful.
- Apply-and-leave must wait for the completed Root batch and its verified state; failures stay in the editor.
- While Root is busy, disable Apply and Discard; warn when a refresh is incomplete.

## Cross-user restore
- In two Android user spaces, disable a tile/widget separately and verify that reboot restoration respects each user ID.
- Removed, locked, or inaccessible Android users must be skipped safely, leaving saved intent intact.
- Re-discover actual target user components before changing them. Never fall back to the current profile's PackageManager.
- Protection: never auto-disable Android platform, SystemUI or the ListCleaner manager itself.
- Check diagnostic reconcile counts for missing, mismatched, repaired, failed and partial scans.

## UI and performance
- Verify the shared beta editors under large fonts, small screens, dark/light themes and both Android navigation modes.
- LSPosed global display modes change resolver filtering, not Root component override semantics.
- Confirm that installed-package revisions do not trigger redundant list grouping.

## Release
- Run a single Debug CI after all edits; arm64 build, lint/checks and unit regression tests.
- On-device validation is still required for Root, LSPosed, OEM pickers and multi-space behaviors.
