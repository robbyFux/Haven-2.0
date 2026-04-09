"""
Template tags for the events app.
"""

from django import template

register = template.Library()


@register.simple_tag(takes_context=False)
def query_replace(request, **kwargs):
    """
    Return the current query string with the specified keys replaced/added.

    Usage in template:
        ?{% query_replace request page=2 %}
    """
    params = request.GET.copy()
    for key, value in kwargs.items():
        params[key] = value
    return params.urlencode()
