# gw_paste98

A notification area ("system tray") program for Windows 98 that sends text to
the Glitch Works pastes service over modern TLS, and puts the new paste's
address on the clipboard.

It needs Java 5 (1.5.0_22 is what it was tested against). Encryption is TLS 1.2
from Bouncy Castle, built into `GWPASTE.JAR`, so Java 5's outdated TLS support
and certificate list are not used at all.

## Files

| File | What it is |
|---|---|
| `GWPASTE.JAR` | The program |
| `GWTRAY.DLL` | Lets Java 5 show a tray icon (Java 5 has no tray support of its own) |
| `GWTRAY.ICO` | The tray icon |
| `PASTE98.CFG` | Settings: server and key pin. Edit this before first use. |
| `PASTE98.BAT` | Starts the program |
| `SENDFILE.BAT` | Sends a text file from the command line |
| `PASTE98.LOG` | Written each time the program runs; look here if something goes wrong |
| `PASTE98.USR` | Your remembered username, if you ticked "Remember username" |

## Setting up

1. Copy the `gw_paste98` folder anywhere, for example `C:\GW_PASTE98`.
2. Edit `PASTE98.CFG` with Notepad and replace `REPLACE_WITH_LIVE_KEY_PIN`
   with the server's key pin (next section).
3. Run `PASTE98.BAT`. A clipboard icon appears in the notification area.

To start it with Windows, make a shortcut in
`C:\WINDOWS\Start Menu\Programs\StartUp` with the target

    "C:\Program Files\Java\jre1.5.0_22\bin\javaw.exe" -Xmx32m -jar C:\GW_PASTE98\GWPASTE.JAR

adjusting both paths to where Java 5 and the program actually are.

## The key pin

The program trusts the server only if its public key matches the pin in
`PASTE98.CFG`: the base64 SHA-256 hash of the key. It does not use any
certificate authority, so Let's Encrypt renewals do not matter as long as the
server keeps the same key.

On the server (OpenBSD / LibreSSL), from the key relayd uses:

    openssl pkey -in /etc/ssl/private/pastes.glitchwrks.com.key -pubout -outform der \
      | openssl dgst -sha256 -binary | openssl base64

Put a second pin after the first, separated by a space, for a backup key that
the server can be switched to without updating every client.

## Using it

Right-click the tray icon for the menu; double-click it to open a new paste.

- **New paste...** opens an editor. Type or paste text and press Send (or
  Ctrl+Enter).
- **Paste clipboard** sends the text on the clipboard after asking you to
  confirm.
- **Status...** shows whether the server is reachable, the encryption in use
  and the last paste's address.
- **Exit** removes the icon and quits.

The first time you send, it asks for your pastes username and password. The
password is kept in memory only, until you exit; it is never written to disk.

When a paste is created, its address (for example
`https://pastes.glitchwrks.com/k3x9q2ab`) is copied to the clipboard.

At start-up the program connects to the server once in the background. That
first connection takes a couple of seconds on a Pentium-class machine; after
it, pastes take about half a second.

## Limits

- At most 65,535 characters per paste (the server's limit).
- The server stores text as Latin-1. Characters outside it, such as the euro
  sign, are stored as `?`. The program warns before sending such text.

## Command line

    SENDFILE filename

sends a text file and prints the paste's address. The password comes from the
`PASTE98_PASSWORD` environment variable and the username from `PASTE98.USR`
(or add `--user NAME`). For example:

    SET PASTE98_PASSWORD=yourpassword
    SENDFILE AUTOEXEC.BAT

## If something goes wrong

- **No tray icon, a small window instead:** the program could not use
  `GWTRAY.DLL`. The window does the same things. The reason is at the bottom
  of the window and in `PASTE98.LOG`.
- **"Server key mismatch":** the server presented a different key from the pin.
  Nothing was sent. Either the server's key changed (update the pins) or
  something is intercepting the connection.
- **"Login failed":** wrong username or password. You will be asked again.
- **"Offline" in Status:** the server could not be reached at start-up. Sending
  a paste tries again.

## Licence

Copyright (C) 2026 Glitch Works, LLC. Free software under the GNU General
Public License, version 3 or later; see `LICENSE.TXT`. Includes Bouncy Castle
(MIT licence).
