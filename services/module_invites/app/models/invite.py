"""Table ``invites`` : une personne susceptible d'être invitée."""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, CheckConstraint, Identity, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.invitation import Invitation


class Invite(Base):
    __tablename__ = "invites"
    __table_args__ = (
        CheckConstraint("email IS NOT NULL OR telephone IS NOT NULL", name="ck_invites_contact"),
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=False), primary_key=True)
    nom: Mapped[str] = mapped_column(String(150), nullable=False)
    email: Mapped[str | None] = mapped_column(String(255))
    telephone: Mapped[str | None] = mapped_column(String(50))

    # lazy="raise" : aucun chargement implicite, les jointures sont écrites dans les repositories.
    # passive_deletes="all" : l'ORM ne tente jamais de modifier les invitations à la suppression
    # d'un invité (c'est la clé étrangère PostgreSQL qui protège).
    invitations: Mapped[list[Invitation]] = relationship(
        back_populates="invite", lazy="raise", passive_deletes="all"
    )
