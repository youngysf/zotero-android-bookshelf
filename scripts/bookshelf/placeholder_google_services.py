#!/usr/bin/env python3
"""Overwrites app/google-services.json with a placeholder, in CI only.

Zotero's build uses Google's Firebase plugin for crash reporting, and that
plugin refuses to build unless the file lists the app's package name. The
committed file is Zotero's own and only lists org.zotero.android. Bookshelf
turns crash reporting off, so the replacement only needs to be well-formed;
it points at no real project. Run this on the build machine, not locally,
and don't commit the result.
"""
import json
import pathlib
import sys

app_id = sys.argv[1] if len(sys.argv) > 1 else "org.zotero.android.bookshelf"
config = {
    "project_info": {
        "project_number": "000000000000",
        "project_id": "bookshelf-placeholder",
        "storage_bucket": "bookshelf-placeholder.appspot.com",
    },
    "client": [
        {
            "client_info": {
                "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
                "android_client_info": {"package_name": package},
            },
            "oauth_client": [],
            # Placeholder only; this key is not valid for any Google service.
            "api_key": [{"current_key": "AIzaSy" + "0" * 33}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }
        for package in (app_id, app_id + ".debug")
    ],
    "configuration_version": "1",
}
out = pathlib.Path(__file__).resolve().parents[2] / "app" / "google-services.json"
out.write_text(json.dumps(config, indent=2))
print(f"Wrote placeholder {out} for {app_id}")
