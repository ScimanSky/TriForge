"""Encryption of Garmin session material at rest.

Garmin tokens grant long-lived access to an athlete's account, and a pending
MFA state holds live session cookies. Both are encrypted before they are
written to Firestore, so a leak of the database alone does not expose them.
"""

from __future__ import annotations

from cryptography.fernet import Fernet, InvalidToken


class TokenCipher:
    def __init__(self, key: str) -> None:
        self._fernet = Fernet(key.encode("utf-8"))

    def encrypt(self, plaintext: str) -> str:
        return self._fernet.encrypt(plaintext.encode("utf-8")).decode("ascii")

    def decrypt(self, ciphertext: str) -> str | None:
        """Return the plaintext, or None when it cannot be decrypted.

        None covers a rotated key and corrupted data alike: in both cases the
        athlete has to connect Garmin again.
        """
        try:
            return self._fernet.decrypt(ciphertext.encode("ascii")).decode("utf-8")
        except (InvalidToken, ValueError):
            return None
