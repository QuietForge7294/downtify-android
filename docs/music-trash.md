# Administrator music trash

This client requires the opt-in music trash API proposed in the server
repository. It does not enable or configure physical deletion on its own.

An administrator can select one or more library tracks, inspect the server's
verified preview and confirm moving those selected files to trash. Only the
selected copies move; another MP3/FLAC copy is not implicitly selected.

The Trash view supports search, selection, restoration and operation states.
Files remain recoverable for 30 days after the worker actually moves them.
There is no action to expire them early. Restore collisions and filesystem
failures retain the files and show an error instead of claiming success.

The client asks the server for capabilities on the current pairing. Old or
unconfigured servers and non-administrator pairings do not expose the action.
Every mutation is also authorized again on the server. Account changes clear
the current trash UI and cancel outstanding requests.

Library sync preserves server track IDs. Confirmed removed tracks are removed
from the playback queue only after a successful authoritative synchronization;
restored offline sessions are not cleared before that first successful sync.
Persistent deletion receipts remove read-ahead cache copies, retrying when a
cached span is in use. Existing offline reconciliation handles orphaned audio.
Restoring a track does not automatically resume playback.

The server's separate worker owns filesystem moves, retention and restoration.
The Android application never directly manipulates NAS paths. Full validation
on an actual phone against the configured server remains necessary before
releasing this experimental client feature.
