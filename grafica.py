"""Genera la grafica de desempeno de la Parte III a partir de resultados_desempeno.csv."""
import csv, sys, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

CSV = sys.argv[1] if len(sys.argv) > 1 else "resultados_desempeno.csv"
OUT = sys.argv[2] if len(sys.argv) > 2 else "img/desempeno.png"
TOTAL_LISTS = 80000

hilos, tiempo, speedup, creacion = [], [], [], []
with open(CSV, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        hilos.append(int(row["hilos"]))
        tiempo.append(int(row["tiempo_ms"]))
        speedup.append(float(row["speedup"]))
        creacion.append(int(row["creacion_ms"]))

cores = 16
per_query = [t * n / TOTAL_LISTS for t, n in zip(tiempo, hilos)]
best = min(range(len(tiempo)), key=lambda i: tiempo[i])

fig, ax = plt.subplots(2, 2, figsize=(13, 9))
fig.suptitle("Parte III - Desempeno vs. numero de hilos  (16 nucleos, IP 202.24.34.55)",
             fontsize=13, fontweight="bold")

# (1) tiempo vs hilos
a = ax[0][0]
a.plot(hilos, tiempo, "o-", color="#2b6cb0", lw=2)
a.axvline(cores, ls="--", c="gray", lw=1)
a.text(cores, max(tiempo)*0.5, " 16 nucleos", fontsize=8, color="gray", rotation=90, va="center")
a.plot(hilos[best], tiempo[best], "o", ms=13, mfc="none", mec="#c53030", mew=2)
a.annotate(f"minimo: {hilos[best]} hilos\n{tiempo[best]} ms",
           (hilos[best], tiempo[best]), textcoords="offset points", xytext=(12, 26),
           fontsize=9, color="#c53030",
           arrowprops=dict(arrowstyle="->", color="#c53030", lw=1))
a.set_xscale("log"); a.set_yscale("log")
a.set_xlabel("hilos (escala log)"); a.set_ylabel("tiempo (ms, escala log)")
a.set_title("Tiempo de solucion", fontsize=11)
a.grid(True, which="both", alpha=.3)

# (2) speedup real vs ideal
a = ax[0][1]
a.plot(hilos, speedup, "o-", color="#2f855a", lw=2, label="speedup medido")
a.plot(hilos, hilos, "--", color="#a0aec0", lw=1.5, label="ideal S(n)=n")
a.axhline(cores, ls=":", c="#c53030", lw=1.5, label=f"techo si fuera CPU-bound ({cores}x)")
a.set_xscale("log"); a.set_yscale("log")
a.set_xlabel("hilos (escala log)"); a.set_ylabel("speedup  T(1)/T(n)")
a.set_title("Speedup: supera el numero de nucleos", fontsize=11)
a.legend(fontsize=8); a.grid(True, which="both", alpha=.3)

# (3) costo por consulta por hilo -> el quiebre
a = ax[1][0]
colors = ["#2f855a" if p < 2.5 else "#c53030" for p in per_query]
a.bar([str(h) for h in hilos], per_query, color=colors)
a.axhline(1.55, ls="--", c="gray", lw=1.2)
a.set_yscale("log")
a.set_ylim(0.8, 120)
a.text(len(hilos) - 0.4, 1.62, "costo sano ~1.55 ms", fontsize=8.5,
       color="gray", ha="right", va="bottom")
a.set_xlabel("hilos"); a.set_ylabel("ms por consulta, por hilo (log)")
a.set_title("Contencion: plano hasta 200, luego explota", fontsize=11)
a.grid(True, axis="y", which="both", alpha=.3)
for i, p in enumerate(per_query):
    a.text(i, p * 1.08, f"{p:.1f}", ha="center", va="bottom", fontsize=8)

# (4) descomposicion del tiempo
a = ax[1][1]
sub = [i for i, h in enumerate(hilos) if h >= 100]
labels = [str(hilos[i]) for i in sub]
crea = [creacion[i] for i in sub]
resto = [tiempo[i] - creacion[i] for i in sub]
a.bar(labels, resto, label="busqueda", color="#4299e1")
a.bar(labels, crea, bottom=resto, label="creacion de hilos", color="#ed8936")
a.set_xlabel("hilos"); a.set_ylabel("ms")
a.set_title("De que se compone el tiempo (>=100 hilos)", fontsize=11)
a.legend(fontsize=8); a.grid(True, axis="y", alpha=.3)

plt.tight_layout(rect=[0, 0, 1, 0.96])
os.makedirs(os.path.dirname(OUT) or ".", exist_ok=True)
plt.savefig(OUT, dpi=150)
print("Grafica generada en", OUT)
