"""Table ``invitations`` : lien invité ↔ événement, avec la réponse (``statut``) et ``date_envoi``.

- ``statut``     = réponse de l'invité (EN_ATTENTE, ACCEPTEE, REFUSEE).
- ``date_envoi`` = NULL tant que l'envoi n'a pas été confirmé par le service d'envoi.
- L'état technique de l'envoi (``etat_envoi``) est CALCULÉ à partir de ``date_envoi``.

Référence : le schéma de la base commune EventFlow (``database/init/001_schema.sql`` du dépôt de
l'équipe). Il contient déjà l'unicité ``uk_invitations_invite_evenement`` et l'index
``idx_invitations_evenement_id`` ; il n'a PAS de contrainte CHECK sur ``statut`` (la validation est
faite par l'application ; voir ``sql/ameliorations_recommandees.sql``). Les noms ci-dessous sont
ceux de ce schéma. Les tables sont créées par les scripts de l'équipe (``database/init``), pas par ce
module : il n'y a pas de migration Alembic.

La table ``evenements`` appartient au service Événements : le module ne la lit ni ne la modifie
(il interroge ce service en HTTP). Elle n'est déclarée plus bas que comme cible de la clé étrangère.
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    Column,
    DateTime,
    ForeignKey,
    Identity,
    Index,
    String,
    Table,
    UniqueConstraint,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.enums import EtatEnvoi, StatutInvitation, calculer_etat_envoi
from app.database.base import Base

if TYPE_CHECKING:
    from app.models.invite import Invite

# Cible de la clé étrangère uniquement (pas un modèle, jamais lue ni écrite par ce module).
EVENEMENTS_REFERENCE = Table("evenements", Base.metadata, Column("id", BigInteger, primary_key=True))

CONTRAINTE_UNICITE = "uk_invitations_invite_evenement"
CONTRAINTE_FK_INVITE = "fk_invitations_invite"
CONTRAINTE_FK_EVENEMENT = "fk_invitations_evenement"
CONTRAINTE_STATUT = "ck_invitations_statut"
INDEX_EVENEMENT = "idx_invitations_evenement_id"


class Invitation(Base):
    __tablename__ = "invitations"
    __table_args__ = (
        CheckConstraint(
            "statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE')",
            name=CONTRAINTE_STATUT,
        ),
        UniqueConstraint("invite_id", "evenement_id", name=CONTRAINTE_UNICITE),
        Index(INDEX_EVENEMENT, "evenement_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=False), primary_key=True)
    invite_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("invites.id", name=CONTRAINTE_FK_INVITE, ondelete="CASCADE"),
        nullable=False,
    )
    evenement_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("evenements.id", name=CONTRAINTE_FK_EVENEMENT, ondelete="RESTRICT"),
        nullable=False,
    )
    date_envoi: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    statut: Mapped[str] = mapped_column(
        String,
        nullable=False,
        default=StatutInvitation.EN_ATTENTE.value,
        server_default=text("'EN_ATTENTE'"),
    )

    invite: Mapped[Invite] = relationship(back_populates="invitations", lazy="raise")

    @property
    def etat_envoi(self) -> EtatEnvoi:
        """Calculé, jamais stocké : ENVOYEE si ``date_envoi`` est renseignée, sinon NON_ENVOYEE."""
        return calculer_etat_envoi(self.date_envoi)
