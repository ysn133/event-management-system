import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { CalendarDays, ArrowRight, Eye, EyeOff, LockKeyhole, Mail } from 'lucide-react'
import { login } from './services/authService'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setBusy(true); setError('')
    try { await login(email.trim(), password) }
    catch (error) { setError(axios.isAxiosError(error) && error.response?.status === 401
      ? 'Email ou mot de passe incorrect.' : 'Connexion impossible. Vérifiez que le service Authentification est démarré.') }
    finally { setBusy(false) }
  }
  return <div className="login-page">
    <section className="login-story"><div className="login-brand"><span><CalendarDays size={24} /></span>EventFlow</div><div className="login-story-copy"><span className="login-eyebrow">VOTRE ESPACE ÉVÉNEMENTIEL</span><h1>Chaque détail compte.<br />Gardez une vue d’ensemble.</h1><p>Organisez vos événements et suivez vos budgets dans un seul espace.</p><div className="login-decoration" aria-hidden="true"><div><CalendarDays size={20} /><span>Vos événements<strong>Tout commence ici.</strong></span></div><div><LockKeyhole size={20} /><span>Votre espace sécurisé<strong>Connectez-vous pour continuer.</strong></span></div></div></div><small>EventFlow · Gestion événementielle</small></section>
    <main className="login-panel"><div className="login-form-wrap"><span className="login-welcome-icon"><LockKeyhole size={25} /></span><h2>Bienvenue sur EventFlow</h2><p>Connectez-vous pour accéder à votre espace.</p><form onSubmit={submit}>
      <label htmlFor="login-email">Adresse email</label><div className="login-input"><Mail size={18} /><input id="login-email" type="email" autoComplete="username" required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} placeholder="vous@exemple.com" /></div>
      <label htmlFor="login-password">Mot de passe</label><div className="login-input"><LockKeyhole size={18} /><input id="login-password" type={visible ? 'text' : 'password'} autoComplete="current-password" required maxLength={72} value={password} onChange={event => setPassword(event.target.value)} placeholder="Votre mot de passe" /><button type="button" aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button></div>
      {error && <p className="login-error" role="alert">{error}</p>}
      <button className="login-submit" disabled={busy}>{busy ? 'Connexion en cours…' : 'Se connecter'}<ArrowRight size={18} /></button>
    </form><p className="login-help">Besoin d’un accès ? Contactez le responsable du projet.</p></div></main>
  </div>
}
