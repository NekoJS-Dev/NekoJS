# Minimal Source-RCON client for the ticket 02 sampler (fixed channel).
# The pure-PowerShell framing in sample.ps1 v1 got the connection reset by the
# vanilla RCON thread on auth; this helper is the verified working channel.
# Usage: python rcon.py <port> <password> <timeout_seconds> <command> [command...]
# Prints each response body to stdout; exits 1 if auth fails or any command errors.
import socket
import struct
import sys


def recv_packet(sock):
    header = b""
    while len(header) < 4:
        chunk = sock.recv(4 - len(header))
        if not chunk:
            return None
        header += chunk
    (size,) = struct.unpack("<i", header)
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            return None
        data += chunk
    rid, rtype = struct.unpack("<ii", data[:8])
    return rid, rtype, data[8:size - 2].decode("ascii", errors="replace")


def send_packet(sock, rid, rtype, payload):
    body = struct.pack("<ii", rid, rtype) + payload.encode("ascii") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)


def main():
    if len(sys.argv) < 5:
        print("usage: rcon.py <port> <password> <timeout_seconds> <command> [command...]", file=sys.stderr)
        return 2
    port, password = int(sys.argv[1]), sys.argv[2]
    timeout = float(sys.argv[3])
    commands = sys.argv[4:]
    sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
    sock.settimeout(timeout)
    try:
        send_packet(sock, 1, 3, password)
        auth = recv_packet(sock)
        if auth is None or auth[0] == -1:
            print("RCON-AUTH-FAILED", file=sys.stderr)
            return 1
        for i, command in enumerate(commands):
            send_packet(sock, 10 + i, 2, command)
            resp = recv_packet(sock)
            if resp is None:
                print("RCON-NO-RESPONSE", file=sys.stderr)
                return 1
            print(resp[2])
        return 0
    finally:
        sock.close()


if __name__ == "__main__":
    sys.exit(main())
