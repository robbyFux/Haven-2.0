"""add pushover_app_token to users

Revision ID: a3f2e1d4c5b6
Revises: 61bdd1bdc836
Create Date: 2026-04-10

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "a3f2e1d4c5b6"
down_revision: Union[str, None] = "61bdd1bdc836"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("pushover_app_token", sa.String(length=50), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("users", "pushover_app_token")
