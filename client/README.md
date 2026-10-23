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
| `PASTE98.BAT` | Starts the program |
| `SENDFILE.BAT` | Sends a text file from the command line |
| `PASTE98.PRF` | Settings, written by the program (created when you first save settings) |
| `PASTE98.BAK` | The previous `PASTE98.PRF`, kept in case saving is interrupted |
| `PASTE98.LOG` | Written each time the program runs; look here if something goes wrong |

## Setting up

1. Copy the `gw_paste98` folder anywhere, for example `C:\GW_PASTE98`.
2. Run `PASTE98.BAT`. A clipboard icon appears in the notification area, and
   the Settings window opens.
3. Enter the server's key pin (next section) and press OK.

**Upgrading from 0.1.x:** copy the new files over the old ones. On first start,
the program copies your settings from `PASTE98.CFG` and `PASTE98.USR`, renames
`PASTE98.CFG` to `PASTE98.OLD` and deletes `PASTE98.USR`.

To start it with Windows, make a shortcut in
`C:\WINDOWS\Start Menu\Programs\StartUp` with the target

    "C:\Program Files\Java\jre1.5.0_22\bin\javaw.exe" -Xmx32m -jar C:\GW_PASTE98\GWPASTE.JAR

adjusting both paths to where Java 5 and the program actually are.

## The key pin

The program trusts the server only if its public key matches a key pin in
Settings: the base64 SHA-256 hash of the key. It does not use any certificate
authority, so Let's Encrypt renewals do not matter as long as the server keeps
the same key.

**Getting it from the server.** Settings > Server > **Get pin from server...**
connects to the host and port you entered and shows the key the server
presented: its pin, key type, the names on its certificate, issuer and dates.
This is NOT verified: it is whatever answered, and if something is
intercepting the connection it is the interceptor's key. The program adds the
pin only after you tick "I have checked this pin against a trusted source",
and warns in red if it differs from pins you already have. A key that changed
when you did not expect it to is a reason to stop, not to add the new pin.

**Checking it.** Compare the pin character by character with one from a
source you trust. The most direct is the key file on the server (OpenBSD /
LibreSSL), from the key relayd uses:

    openssl pkey -in /etc/ssl/private/pastes.glitchwrks.com.key -pubout -outform der \
      | openssl dgst -sha256 -binary | openssl base64

From another machine you trust, you can also ask the server over the network.
Like Get pin from server, this only reports what answered, so it is a check
from a different vantage point, not proof:

    openssl s_client -connect pastes.glitchwrks.com:443 -servername pastes.glitchwrks.com </dev/null 2>/dev/null \
      | openssl x509 -pubkey -noout \
      | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary \
      | openssl base64

Put a second pin on its own line for a backup key that the server can be
switched to without updating every client.

## Using it

Right-click the tray icon for the menu; double-click it to open a new paste.

- **New paste...** opens an editor. Type or paste text and press Send (or
  Ctrl+Enter).
- **Paste clipboard** sends the text on the clipboard after asking you to
  confirm.
- **Status...** shows whether the server is reachable, the encryption in use
  and the last paste's address.
- **Settings...** changes the settings (next section).
- **Exit** removes the icon and quits.

The first time you send, it asks for your pastes username and password. The
password is kept in memory only, until you exit; it is never written to disk.

When a paste is created, its address (for example
`https://pastes.glitchwrks.com/k3x9q2ab`) is copied to the clipboard and shown
in a window; either can be turned off in Settings.

At start-up the program connects to the server once in the background. That
first connection takes a couple of seconds on a Pentium-class machine; after
it, pastes take about half a second.

## Settings

| Tab | Setting | Default |
|---|---|---|
| Server | Host | `pastes.glitchwrks.com` |
| | Port | 443 |
| | Key pins | none: required (Get pin from server... can fetch it) |
| Connection | Cipher | Automatic (ChaCha20 preferred) |
| | Key exchange | Automatic (X25519 preferred) |
| | Timeout | 30 seconds |
| | Connect at start-up to warm up | on |
| Pastes | Ask before sending the clipboard | on |
| | Copy the new paste's address to the clipboard | on |
| | Show the new paste's address in a window | on |
| Account | Remembered username | none |
| Appearance | Look and feel | Java Metal, classic (Steel) |

Changes take effect on the next paste; no restart is needed.

**Appearance.** The program looks like a Java program by default, using
Swing's classic Metal look from Java 1.2 to 1.4. "Java Metal, Ocean" is the
look Java 5 introduced. "Windows" uses the system's look, but not quite its
text: Windows 98 draws dialogs in MS Sans Serif, a bitmap font, and Java can
only draw outline fonts. With "Windows" chosen, the program uses Microsoft
Sans Serif (an outline version of MS Sans Serif, which comes with Windows
2000 and later and some applications) or Tahoma if either is installed, and
otherwise Java's default, Arial. Status shows which font is in use. The tray
menu is drawn by Windows in every case. Changing the host
or port logs you out, so your password is never sent to a different server.

Settings are saved in `PASTE98.PRF` in the program's folder, not in the
Windows registry. **Export...** saves them to an XML file (Java's standard
preferences format) and **Import...** reads one back into the Settings window
for you to review before pressing OK; this is the way to copy settings to
another machine. Exported files contain the server, pins and username, never
the password.

`PASTE98.PRF` is plain text, but edit settings in the Settings window: it
checks every value, while a hand-edited mistake is ignored (with a note in
`PASTE98.LOG`) and the default used instead.

## Limits

- At most 65,535 characters per paste (the server's limit).
- The server stores text as Latin-1. Characters outside it, such as the euro
  sign, are stored as `?`. The program warns before sending such text.

## Command line

    SENDFILE filename

sends a text file and prints the paste's address, using the settings saved by
the tray program. The password comes from the `PASTE98_PASSWORD` environment
variable and the username from the remembered username (or add `--user NAME`).
For example:

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
