"""
Custom Django authentication backend for Haven Web UI.

Verifies passwords against the bcrypt hashes stored in the 'password_hash'
column of the users table (created and maintained by FastAPI/Alembic).

TOTP verification is NOT performed here — it happens in the login view after
the password check succeeds, so the session is only created once both factors
pass.

Threat T-06-01: bcrypt verification provides brute-force resistance through cost
factor; session auth via Django middleware handles the rest.
"""

import bcrypt

from accounts.models import HavenUser

# Pre-computed hash used for constant-time dummy verify (prevents username enumeration timing)
_DUMMY_HASH: bytes = bcrypt.hashpw(b"x", bcrypt.gensalt())


class HavenAuthBackend:
    """
    Authenticate against the shared 'users' table using bcrypt password hashes.

    This backend replaces Django's default ModelBackend. It does NOT check
    Django's built-in permission system — all permission checks are manual
    (user.is_admin guards) in the views.
    """

    def authenticate(self, request, username: str | None = None, password: str | None = None):
        """
        Verify username + bcrypt password.

        Returns the HavenUser instance on success, None on failure.
        TOTP check is left to the calling view.
        """
        if username is None or password is None:
            return None

        try:
            user = HavenUser.objects.get(username=username)
        except HavenUser.DoesNotExist:
            # Run a dummy verify to prevent timing attacks via username enumeration
            bcrypt.checkpw(b"x", _DUMMY_HASH)
            return None

        if not bcrypt.checkpw(password.encode(), user.password_hash.encode()):
            return None

        if not user.is_active:
            return None

        return user

    def get_user(self, user_id: int):
        """
        Load a user by primary key for session hydration.

        Called by Django's AuthenticationMiddleware on every request.
        Returns None if user is not found (session becomes anonymous).
        """
        try:
            return HavenUser.objects.get(pk=user_id)
        except HavenUser.DoesNotExist:
            return None
