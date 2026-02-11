import { Hero } from '@/components/sections/Hero';
import { Constat } from '@/components/sections/Constat';
import { Features } from '@/components/sections/Features';
import { Steps } from '@/components/sections/Steps';
import { Garantie } from '@/components/sections/Garantie';
import { Status } from '@/components/sections/Status';
import { FAQ } from '@/components/sections/FAQ';

export default function HomePage() {
  return (
    <main>
      <Hero />
      <Constat />
      <Features />
      <Steps />
      <Garantie />
      <Status />
      <FAQ namespace="landing.faq" count={6} />
    </main>
  );
}
