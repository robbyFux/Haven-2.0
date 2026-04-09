"""
Reusable view decorators for Haven Web UI.
"""

from functools import wraps

from django.contrib.auth.decorators import login_required
from django.http import HttpResponseForbidden


def admin_required(view_func):
    """
    Decorator that requires the user to be logged in AND have is_admin=True.

    - Unauthenticated requests: redirected to LOGIN_URL (via @login_required).
    - Authenticated non-admin requests: returns HTTP 403 Forbidden.
    - Authenticated admin requests: view is called normally.
    """

    @wraps(view_func)
    @login_required
    def wrapper(request, *args, **kwargs):
        if not request.user.is_admin:
            return HttpResponseForbidden("Admin access required.")
        return view_func(request, *args, **kwargs)

    return wrapper
