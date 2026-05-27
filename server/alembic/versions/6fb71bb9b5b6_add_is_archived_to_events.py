"""add_is_archived_to_events

Revision ID: 6fb71bb9b5b6
Revises: a3f2e1d4c5b6
Create Date: 2026-05-27

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "6fb71bb9b5b6"
down_revision: Union[str, None] = "a3f2e1d4c5b6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "events",
        sa.Column("is_archived", sa.Boolean(), nullable=False, server_default="false"),
    )


def downgrade() -> None:
    op.drop_column("events", "is_archived")
