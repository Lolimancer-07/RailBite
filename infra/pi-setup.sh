#!/usr/bin/env bash
# RailBite Raspberry Pi 4 Pantry Server Setup Script
set -e

echo "=== Setting up RailBite Pantry Network & Edge Server on Raspberry Pi ==="

# 1. Update & install dependencies
sudo apt-get update
sudo apt-get install -y hostapd dnsmasq iptables curl git

# 2. Configure static IP on wlan0 (192.168.50.1)
cat << 'EOF' | sudo tee -a /etc/dhcpcd.conf
interface wlan0
    static ip_address=192.168.50.1/24
    nohook wpa_supplicant
EOF

# 3. Configure hostapd (/etc/hostapd/hostapd.conf)
cat << 'EOF' | sudo tee /etc/hostapd/hostapd.conf
interface=wlan0
driver=nl80211
ssid=RailBite_Pantry
hw_mode=g
channel=7
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
wpa=2
wpa_passphrase=RailBite2024
wpa_key_mgmt=WPA-PSK
wpa_pairwise=TKIP
rsn_pairwise=CCMP
EOF

sudo sed -i 's|#DAEMON_CONF=""|DAEMON_CONF="/etc/hostapd/hostapd.conf"|g' /etc/default/hostapd

# 4. Configure dnsmasq (/etc/dnsmasq.conf)
cat << 'EOF' | sudo tee /etc/dnsmasq.conf
interface=wlan0
dhcp-range=192.168.50.10,192.168.50.200,255.255.255.0,24h
address=/railbite.local/192.168.50.1
EOF

# 5. Enable and start services
sudo systemctl unmask hostapd
sudo systemctl enable hostapd
sudo systemctl enable dnsmasq

echo "=== Network setup ready. Install Node 20/22 and run PM2 ==="
