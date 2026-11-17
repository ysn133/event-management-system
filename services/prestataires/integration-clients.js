const request = async (url, authorization, options = {}) => {
  let response;
  try {
    response = await fetch(url, {
      ...options,
      headers: { 'Content-Type': 'application/json', Authorization: authorization, ...options.headers },
      signal: AbortSignal.timeout(8000),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(body.message || body.detail || 'Le service a refusé la demande.');
      error.status = response.status;
      error.definitive = [400, 401, 403, 404, 409, 422].includes(response.status);
      throw error;
    }
    return body;
  } catch (error) {
    if (error.status) throw error;
    const unavailable = new Error('Un service est indisponible. La synchronisation pourra être réessayée.');
    unavailable.status = 503;
    throw unavailable;
  }
};

module.exports = {
  authorize: authorization => request(`${process.env.AUTH_SERVICE_URL || 'http://localhost:5005'}/api/auth/me`, authorization),
  event: (id, authorization) => request(`${process.env.EVENT_SERVICE_URL || 'http://localhost:5001'}/api/evenements/${id}`, authorization),
  budget: (contract, payload, authorization) => request(
    `${process.env.BUDGET_SERVICE_URL || 'http://localhost:5004'}/api/budgets/evenement/${contract.evenement_id}/contrats-prestataire/${contract.id}`,
    authorization,
    { method: 'PUT', headers: { 'X-Service-Key': process.env.CONTRACT_SERVICE_KEY || '' }, body: JSON.stringify({
      description: payload.prestation,
      montant: payload.montant,
      dateDepense: payload.datePrestation.slice(0, 10),
      statut: payload.statut,
      version: Number(contract.budget_sync_version),
    }) },
  ),
};
