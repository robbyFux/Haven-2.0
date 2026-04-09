"""
Django forms for Haven account auth flows.

Forms are intentionally minimal — validation happens here, security
checks (bcrypt verify, TOTP confirm) happen in views.
"""

from django import forms


class RegisterForm(forms.Form):
    """Registration form: username + password confirmation."""

    username = forms.CharField(
        max_length=50,
        widget=forms.TextInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Username",
                "autocomplete": "username",
            }
        ),
    )
    password = forms.CharField(
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Password",
                "autocomplete": "new-password",
            }
        )
    )
    password_confirm = forms.CharField(
        label="Confirm password",
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Confirm password",
                "autocomplete": "new-password",
            }
        ),
    )

    def clean(self):
        cleaned = super().clean()
        pw = cleaned.get("password")
        pw_confirm = cleaned.get("password_confirm")
        if pw and pw_confirm and pw != pw_confirm:
            raise forms.ValidationError("Passwords do not match.")

        username = cleaned.get("username")
        if username:
            from accounts.models import HavenUser

            if HavenUser.objects.filter(username=username).exists():
                raise forms.ValidationError("Username already taken.")

        return cleaned


class LoginForm(forms.Form):
    """Login form: username + password."""

    username = forms.CharField(
        max_length=50,
        widget=forms.TextInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Username",
                "autocomplete": "username",
            }
        ),
    )
    password = forms.CharField(
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Password",
                "autocomplete": "current-password",
            }
        )
    )


class TotpForm(forms.Form):
    """6-digit TOTP verification code."""

    code = forms.CharField(
        min_length=6,
        max_length=6,
        widget=forms.TextInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none "
                "text-center text-2xl tracking-widest font-mono",
                "placeholder": "000000",
                "inputmode": "numeric",
                "autocomplete": "one-time-code",
                "maxlength": "6",
            }
        ),
    )


class TotpSetupConfirmForm(forms.Form):
    """Code confirmation during 2FA setup."""

    code = forms.CharField(
        min_length=6,
        max_length=6,
        widget=forms.TextInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none "
                "text-center text-2xl tracking-widest font-mono",
                "placeholder": "000000",
                "inputmode": "numeric",
                "autocomplete": "one-time-code",
                "maxlength": "6",
            }
        ),
    )


class ChangePasswordForm(forms.Form):
    """Change password: verify current, then set new."""

    current_password = forms.CharField(
        label="Current password",
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Current password",
                "autocomplete": "current-password",
            }
        ),
    )
    new_password = forms.CharField(
        label="New password",
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "New password",
                "autocomplete": "new-password",
            }
        ),
    )
    new_password_confirm = forms.CharField(
        label="Confirm new password",
        widget=forms.PasswordInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg "
                "text-white focus:border-teal-500 focus:ring-teal-500 focus:outline-none",
                "placeholder": "Confirm new password",
                "autocomplete": "new-password",
            }
        ),
    )

    def clean(self):
        cleaned = super().clean()
        pw = cleaned.get("new_password")
        pw_confirm = cleaned.get("new_password_confirm")
        if pw and pw_confirm and pw != pw_confirm:
            raise forms.ValidationError("New passwords do not match.")
        return cleaned


class DeleteAccountForm(forms.Form):
    """Require typing the username to confirm account deletion."""

    confirm_username = forms.CharField(
        label="Type your username to confirm",
        widget=forms.TextInput(
            attrs={
                "class": "w-full px-4 py-2 bg-gray-700 border border-red-600 rounded-lg "
                "text-white focus:border-red-500 focus:ring-red-500 focus:outline-none",
                "placeholder": "Your username",
                "autocomplete": "off",
            }
        ),
    )
