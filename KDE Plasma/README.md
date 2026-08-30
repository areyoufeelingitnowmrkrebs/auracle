# KDE Plasma
The Plasma version is a native wallpaper plugin written in QML.

## Install
1. Simply copy-and-paste this command block:
```bash
mkdir -p ~/.local/share/plasma/wallpapers && \
git clone https://github.com/areyoufeelingitnowmrkrebs/auracle && \
mv auracle/KDE\ Plasma/io.github.areyoufeelingitnowmrkrebs.auracle ~/.local/share/plasma/wallpapers && \
rm -rf auracle
```
2. Go to `System Settings > Wallpaper` and set `Wallpaper type:` to **Auracle**

## Plasma Login Manager (PLM)
To use Auracle with PLM, you'll need to install it system-wide at `/usr/share/plasma/wallpapers/`. It will not show up in `System Settings > Login Screen > Configure Appearance... > Wallpaper type:`. Instead, you'll need to edit PLM's configuration file, for which I've added steps to the Arch Wiki [here](https://wiki.archlinux.org/title/Plasma_Login_Manager#:~:text=.-,Custom%20wallpaper%20plugins).
